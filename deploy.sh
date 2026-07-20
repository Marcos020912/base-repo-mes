#!/usr/bin/env bash
set -euo pipefail

# Interactive deployment for Debian/Ubuntu. Run from the project directory.
if [[ $EUID -ne 0 ]]; then echo "Ejecute con sudo: sudo ./deploy.sh"; exit 1; fi
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
CONF="$APP_DIR/config/application.properties"
REPO_DATA_DIR="/var/lib/base-repo/data"
# application.properties contiene secretos de cada instalación y no se publica
# en Git. En un clon nuevo se genera desde la plantilla versionada.
if [[ ! -s "$CONF" ]]; then
  [[ -e "$CONF" ]] && cp "$CONF" "$CONF.empty.$(date +%s)"
  cp "$APP_DIR/config/application-default.properties" "$CONF"
  echo "Creado $CONF desde application-default.properties."
fi
ask(){
  local var=$1 prompt=$2 default=${3:-} value=""
  # El despliegue es interactivo. Leer desde la terminal evita que una entrada
  # redirigida o agotada termine el script silenciosamente a mitad de los datos.
  if ! read -r -p "$prompt${default:+ [$default]}: " value </dev/tty; then
    echo "No se pudo leer '$prompt'. Ejecute el script desde una terminal interactiva." >&2
    exit 1
  fi
  printf -v "$var" '%s' "${value:-$default}"
}
install_if_missing(){ command -v "$1" >/dev/null 2>&1 || { apt-get update; apt-get install -y "$2"; }; }
# Obtiene el último valor de una clave YAML simple sin interpretar la clave como una expresión regular.
property_value(){
  [[ -r "$CONF" ]] || return 0
  awk -v key="$1" 'index($0, key ":") == 1 { value=substr($0, length(key)+2); sub(/^[[:space:]]+/, "", value) } END { print value }' "$CONF"
}
boolean_value(){
  case "${1,,}" in s|si|sí|y|yes|true|1) printf 'true\n' ;; n|no|false|0) printf 'false\n' ;; *) printf '%s\n' "$1" ;; esac
}
remove_managed_properties(){
  local tmp keys
  keys='server.port,server.address,server.forward-headers-strategy,server.tomcat.remoteip.remote-ip-header,server.tomcat.remoteip.protocol-header,spring.datasource.driver-class-name,spring.datasource.url,spring.datasource.username,spring.datasource.password,spring.jpa.database,spring.jpa.database-platform,repo.basepath,repo.search.url,repo.search.enabled,repo.mail.description,spring.mail.host,spring.mail.port,spring.mail.username,spring.mail.password,spring.mail.properties.mail.smtp.auth,spring.mail.properties.mail.smtp.starttls.enable,spring.mail.properties.mail.smtp.starttls.required,spring.mail.properties.mail.smtp.ssl.trust,spring.mail.properties.mail.smtp.ssl.checkserveridentity,repo.mail.from,repo.allowed-origin-pattern,repo.public-domain,repo.deploy.db-name,repo.deploy.haproxy-network,repo.deploy.private-host,repo.deploy.haproxy-port'
  tmp="$(mktemp "$CONF.XXXXXX")"
  awk -v keys="$keys" '
    BEGIN { count=split(keys, items, ","); for (i=1; i<=count; i++) managed[items[i]]=1 }
    /^[[:space:]]*#/ { print; next }
    {
      line=$0; sub(/^[[:space:]]+/, "", line); separator=index(line, ":")
      if (separator > 0) {
        key=substr(line, 1, separator-1); sub(/[[:space:]]+$/, "", key)
        if (key in managed) next
      }
      print
    }
  ' "$CONF" > "$tmp"
  mv "$tmp" "$CONF"
}
configure_gradle_proxy(){
  local proxy hostport host port
  proxy="${HTTPS_PROXY:-${https_proxy:-}}"
  if [[ -z "$proxy" ]]; then
    proxy="$(grep -RhsE 'Acquire::https::Proxy[[:space:]]+"[^"]+"' /etc/apt/apt.conf /etc/apt/apt.conf.d 2>/dev/null | head -1 | sed -E 's/.*"([^"]+)".*/\1/' || true)"
  fi
  [[ -z "$proxy" || "$proxy" == "DIRECT" ]] && return
  proxy="${proxy#http://}"; proxy="${proxy#https://}"; hostport="${proxy%%/*}"
  # Las configuraciones APT pueden incluir usuario:contraseña@host:puerto.
  # El host nunca debe incluir esas credenciales.
  hostport="${hostport##*@}"
  host="${hostport%:*}"; port="${hostport##*:}"
  [[ -n "$host" && "$port" =~ ^[0-9]+$ ]] || return
  echo "Configurando Gradle para usar el proxy HTTP(S) detectado: $host:$port"
  export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttp.proxyHost=$host -Dhttp.proxyPort=$port -Dhttps.proxyHost=$host -Dhttps.proxyPort=$port"
}
find_elasticsearch_home(){
  local candidate
  for candidate in "${ES_HOME:-}" "$APP_DIR/elasticsearch" \
    "/home/${SUDO_USER:-}/elasticsearch"; do
    [[ -n "$candidate" && -x "$candidate/bin/elasticsearch" ]] && { printf '%s\n' "$candidate"; return; }
  done
  candidate="$(find /home -maxdepth 5 -type f -path '*/bin/elasticsearch' -print -quit 2>/dev/null || true)"
  [[ -n "$candidate" ]] && dirname "$(dirname "$candidate")"
}
find_application_jar(){
  local candidate
  for candidate in "$APP_DIR/build/libs/base-repo.jar" "$APP_DIR/build/libs/base_repo.jar"; do
    [[ -f "$candidate" ]] && { printf '%s\n' "$candidate"; return; }
  done
  find "$APP_DIR/build/libs" -maxdepth 1 -type f -name '*repo*.jar' ! -name '*plain*.jar' -print -quit 2>/dev/null || true
}

configure_elasticsearch_tar(){
  local es_home es_owner
  es_home="$(find_elasticsearch_home)"
  if [[ -z "$es_home" ]]; then
    ask es_home "Ruta de Elasticsearch extraído (debe contener bin/elasticsearch)" "/home/${SUDO_USER:-ituser}/elasticsearch"
  fi
  [[ -x "$es_home/bin/elasticsearch" ]] || { echo "No se encontró Elasticsearch en: $es_home" >&2; exit 1; }
  es_owner="$(stat -c '%U' "$es_home")"
  [[ "$es_owner" != "root" ]] || { echo "El directorio de Elasticsearch no debe ejecutarse como root. Ajuste su propietario a un usuario de servicio." >&2; exit 1; }

  echo "Configurando Elasticsearch desde $es_home…"
  cp "$es_home/config/elasticsearch.yml" "$es_home/config/elasticsearch.yml.bak.$(date +%s)" 2>/dev/null || true
  cat > "$es_home/config/elasticsearch.yml" <<'EOF'
cluster.name: base-repo
node.name: base-repo-node
discovery.type: single-node
network.host: 127.0.0.1
http.port: 9200
# Base Repo usa el cliente HTTP local sin credenciales. Elasticsearch no queda
# expuesto a la red porque escucha exclusivamente en 127.0.0.1.
xpack.security.enabled: false
xpack.security.http.ssl.enabled: false
xpack.security.transport.ssl.enabled: false
EOF
  install -d -m 0755 "$es_home/config/jvm.options.d"
  printf '%s\n' '-Xms1g' '-Xmx1g' > "$es_home/config/jvm.options.d/base-repo.options"
  chown -R "$es_owner":"$(stat -c '%G' "$es_home")" "$es_home/config"
  # Requisito de bootstrap de Elasticsearch en Linux (el paquete DEB lo ajusta
  # automáticamente; la distribución .tar.gz no).
  printf '%s\n' 'vm.max_map_count=262144' > /etc/sysctl.d/99-base-repo-elasticsearch.conf
  sysctl -w vm.max_map_count=262144 >/dev/null

  cat > /etc/systemd/system/base-repo-elasticsearch.service <<EOF
[Unit]
Description=Elasticsearch for Base Repo
After=network.target

[Service]
Type=simple
User=$es_owner
WorkingDirectory=$es_home
Environment=ES_PATH_CONF=$es_home/config
ExecStart=$es_home/bin/elasticsearch
Restart=on-failure
RestartSec=10
LimitNOFILE=65535
TimeoutStopSec=0

[Install]
WantedBy=multi-user.target
EOF
  # Puede existir una instancia iniciada manualmente durante la configuración
  # automática inicial. Se detiene para que no ocupe el puerto 9200.
  systemctl stop elasticsearch base-repo-elasticsearch 2>/dev/null || true
  pkill -u "$es_owner" -f 'org.elasticsearch.bootstrap.Elasticsearch' 2>/dev/null || true
  sleep 2
  systemctl daemon-reload
  systemctl enable --now base-repo-elasticsearch
}

install_if_missing java openjdk-21-jdk
install_if_missing psql postgresql
install_if_missing curl curl

configure_elasticsearch_tar
systemctl enable --now postgresql
for _ in $(seq 1 60); do curl -fsS http://localhost:9200 >/dev/null 2>&1 && break; sleep 1; done
curl -fsS http://localhost:9200 >/dev/null || { echo "Elasticsearch no pudo iniciar. Revise: journalctl -u base-repo-elasticsearch -n 100 --no-pager"; exit 1; }

REUSE_CONFIGURATION="N"
if grep -qE '^# (BEGIN )?Managed by deploy.sh' "$CONF" 2>/dev/null; then
  ask REUSE_CONFIGURATION "Se detectó una configuración previa. ¿Reutilizarla sin volver a pedir datos? (S/n)" "S"
fi
if [[ "${REUSE_CONFIGURATION,,}" == "s" || "${REUSE_CONFIGURATION,,}" == "si" || "${REUSE_CONFIGURATION,,}" == "sí" ]]; then
  APP_PORT="$(property_value 'server.port')"; APP_DOMAIN="$(property_value 'repo.public-domain')"; APP_BIND="$(property_value 'server.address')"
  DB_NAME="$(property_value 'repo.deploy.db-name')"; DB_USER="$(property_value 'spring.datasource.username')"; DB_PASSWORD="$(property_value 'spring.datasource.password')"
  MAIL_DESCRIPTION="$(property_value 'repo.mail.description')"; MAIL_HOST="$(property_value 'spring.mail.host')"; MAIL_PORT="$(property_value 'spring.mail.port')"; MAIL_USER="$(property_value 'spring.mail.username')"; MAIL_PASSWORD="$(property_value 'spring.mail.password')"; MAIL_STARTTLS="$(property_value 'spring.mail.properties.mail.smtp.starttls.enable')"; ES_URL="$(property_value 'repo.search.url')"
  HAPROXY_NETWORK="$(property_value 'repo.deploy.haproxy-network')"; APP_PRIVATE_HOST="$(property_value 'repo.deploy.private-host')"; HAPROXY_FRONTEND_PORT="$(property_value 'repo.deploy.haproxy-port')"; CONFIGURE_FIREWALL="N"
  # Compatibilidad con configuraciones creadas por versiones anteriores del script.
  APP_PORT=${APP_PORT:-8090}; APP_DOMAIN=${APP_DOMAIN:-localhost}; APP_BIND=${APP_BIND:-0.0.0.0}
  DB_NAME=${DB_NAME:-base_repo}; DB_USER=${DB_USER:-base_repo}
  MAIL_DESCRIPTION=${MAIL_DESCRIPTION:-webmail.mes.gob.cu}; MAIL_HOST=${MAIL_HOST:-webmail.mes.gob.ci}; MAIL_PORT=${MAIL_PORT:-25}; MAIL_USER=${MAIL_USER:-soporte@mes.gob.cu}; MAIL_STARTTLS=${MAIL_STARTTLS:-true}
  MAIL_STARTTLS="$(boolean_value "$MAIL_STARTTLS")"
  ES_URL=${ES_URL:-http://localhost:9200}; APP_PRIVATE_HOST=${APP_PRIVATE_HOST:-$(hostname -I | awk '{print $1}')}; HAPROXY_FRONTEND_PORT=${HAPROXY_FRONTEND_PORT:-443}
  if [[ -z "$DB_PASSWORD" || -z "$MAIL_PASSWORD" ]]; then
    echo "La configuración anterior no contiene todas las credenciales; se solicitarán para completar la actualización."
    [[ -n "$DB_PASSWORD" ]] || ask DB_PASSWORD "Contraseña PostgreSQL"
    [[ -n "$MAIL_PASSWORD" ]] || ask MAIL_PASSWORD "Contraseña SMTP"
  fi
  echo "Reutilizando la configuración existente para $APP_DOMAIN."
else
  ask APP_PORT "Puerto de Base Repo" "$(property_value 'server.port')"; APP_PORT=${APP_PORT:-8090}
  ask APP_DOMAIN "Dominio público (sin http)" "$(property_value 'repo.public-domain')"; APP_DOMAIN=${APP_DOMAIN:-localhost}
  ask APP_BIND "IP de escucha interna de Base Repo" "$(property_value 'server.address')"; APP_BIND=${APP_BIND:-0.0.0.0}
  ask HAPROXY_NETWORK "IP o red CIDR del HAProxy remoto (ej. 10.20.0.5 o 10.20.0.0/24)" "$(property_value 'repo.deploy.haproxy-network')"
  ask APP_PRIVATE_HOST "IP/DNS privado de este servidor visible por HAProxy" "$(property_value 'repo.deploy.private-host')"; APP_PRIVATE_HOST=${APP_PRIVATE_HOST:-$(hostname -I | awk '{print $1}')}
  ask HAPROXY_FRONTEND_PORT "Puerto HTTPS público de HAProxy" "$(property_value 'repo.deploy.haproxy-port')"; HAPROXY_FRONTEND_PORT=${HAPROXY_FRONTEND_PORT:-443}
  ask DB_NAME "Base de datos PostgreSQL" "$(property_value 'repo.deploy.db-name')"; DB_NAME=${DB_NAME:-base_repo}
  ask DB_USER "Usuario PostgreSQL" "$(property_value 'spring.datasource.username')"; DB_USER=${DB_USER:-base_repo}
  # Las contraseñas existentes se conservan si se deja vacío el campo, sin mostrarlas en pantalla.
  EXISTING_DB_PASSWORD="$(property_value 'spring.datasource.password')"
  ask DB_PASSWORD "Contraseña PostgreSQL"
  DB_PASSWORD=${DB_PASSWORD:-$EXISTING_DB_PASSWORD}
  ask MAIL_DESCRIPTION "Descripción del servidor de correo" "$(property_value 'repo.mail.description')"; MAIL_DESCRIPTION=${MAIL_DESCRIPTION:-webmail.mes.gob.cu}
  ask MAIL_HOST "Servidor SMTP" "$(property_value 'spring.mail.host')"; MAIL_HOST=${MAIL_HOST:-webmail.mes.gob.ci}
  ask MAIL_PORT "Puerto SMTP" "$(property_value 'spring.mail.port')"; MAIL_PORT=${MAIL_PORT:-25}
  ask MAIL_USER "Usuario SMTP" "$(property_value 'spring.mail.username')"; MAIL_USER=${MAIL_USER:-soporte@mes.gob.cu}
  EXISTING_MAIL_PASSWORD="$(property_value 'spring.mail.password')"
  ask MAIL_PASSWORD "Contraseña SMTP"
  MAIL_PASSWORD=${MAIL_PASSWORD:-$EXISTING_MAIL_PASSWORD}
  ask MAIL_STARTTLS "¿El SMTP usa STARTTLS? (S/n)" "$(property_value 'spring.mail.properties.mail.smtp.starttls.enable')"; MAIL_STARTTLS=${MAIL_STARTTLS:-true}
  MAIL_STARTTLS="$(boolean_value "$MAIL_STARTTLS")"
  ask ES_URL "URL de Elasticsearch" "$(property_value 'repo.search.url')"; ES_URL=${ES_URL:-http://localhost:9200}
  ask CONFIGURE_FIREWALL "¿Configurar UFW para que solo HAProxy acceda al puerto de la app? (s/N)" "N"
fi

sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'" | grep -q 1 || sudo -u postgres psql -c "CREATE USER \"$DB_USER\" WITH PASSWORD '$DB_PASSWORD';"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1 || sudo -u postgres createdb -O "$DB_USER" "$DB_NAME"
install -d -m 0750 "$REPO_DATA_DIR"

cp "$CONF" "$CONF.bak.$(date +%s)"
remove_managed_properties
cat >> "$CONF" <<EOF

# BEGIN Managed by deploy.sh
server.port: $APP_PORT
server.address: $APP_BIND
# HAProxy termina TLS y comunica el esquema/host original con X-Forwarded-*.
server.forward-headers-strategy: framework
server.tomcat.remoteip.remote-ip-header: X-Forwarded-For
server.tomcat.remoteip.protocol-header: X-Forwarded-Proto
spring.datasource.driver-class-name: org.postgresql.Driver
spring.datasource.url: jdbc:postgresql://localhost:5432/$DB_NAME
spring.datasource.username: $DB_USER
spring.datasource.password: $DB_PASSWORD
spring.jpa.database: POSTGRESQL
spring.jpa.database-platform: org.hibernate.dialect.PostgreSQLDialect
# Directorio persistente y escribible para los archivos de los repositorios.
repo.basepath: file:$REPO_DATA_DIR/
repo.search.url: $ES_URL
repo.search.enabled: true
repo.mail.description: $MAIL_DESCRIPTION
spring.mail.host: $MAIL_HOST
spring.mail.port: $MAIL_PORT
spring.mail.username: $MAIL_USER
spring.mail.password: $MAIL_PASSWORD
spring.mail.properties.mail.smtp.auth: true
spring.mail.properties.mail.smtp.starttls.enable: $MAIL_STARTTLS
spring.mail.properties.mail.smtp.starttls.required: $MAIL_STARTTLS
# El proveedor indicó aceptar certificados del servidor SMTP.
spring.mail.properties.mail.smtp.ssl.trust: *
spring.mail.properties.mail.smtp.ssl.checkserveridentity: false
repo.mail.from: $MAIL_USER
repo.allowed-origin-pattern: https://$APP_DOMAIN
repo.public-domain: $APP_DOMAIN
repo.deploy.db-name: $DB_NAME
repo.deploy.haproxy-network: $HAPROXY_NETWORK
repo.deploy.private-host: $APP_PRIVATE_HOST
repo.deploy.haproxy-port: $HAPROXY_FRONTEND_PORT
# END Managed by deploy.sh
EOF

# This file is copied to the remote HAProxy administrator; it is not applied locally.
cat > "$APP_DIR/haproxy-base-repo.cfg" <<EOF
# Añadir en el HAProxy remoto (TLS se termina en el frontend HTTPS).
backend base_repo_backend
    option forwardfor
    http-request set-header X-Forwarded-Proto https
    http-request set-header X-Forwarded-Host %[req.hdr(Host)]
    http-request set-header X-Forwarded-Port $HAPROXY_FRONTEND_PORT
    server base_repo $APP_PRIVATE_HOST:$APP_PORT check

# Health check recomendado: GET /actuator/health
EOF

if [[ "${CONFIGURE_FIREWALL,,}" == "s" || "${CONFIGURE_FIREWALL,,}" == "si" || "${CONFIGURE_FIREWALL,,}" == "sí" ]]; then
  if [[ -z "$HAPROXY_NETWORK" ]]; then echo "Debe indicar la IP o red del HAProxy para activar el firewall."; exit 1; fi
  install_if_missing ufw ufw
  ufw allow OpenSSH
  ufw allow from "$HAPROXY_NETWORK" to any port "$APP_PORT" proto tcp
  ufw deny "$APP_PORT"/tcp
  ufw deny 5432/tcp
  ufw deny 9200/tcp
  ufw --force enable
fi

cd "$APP_DIR"
REBUILD_APPLICATION="S"
APP_JAR="$(find_application_jar)"
if [[ -n "$APP_JAR" ]]; then
  ask REBUILD_APPLICATION "Se encontró build/libs/base-repo.jar. ¿Reconstruir la aplicación? (s/N)" "N"
fi
if [[ "${REBUILD_APPLICATION,,}" == "s" || "${REBUILD_APPLICATION,,}" == "si" || "${REBUILD_APPLICATION,,}" == "sí" ]]; then
  configure_gradle_proxy
  ./gradlew --no-daemon -Dprofile=minimal bootJar
  APP_JAR="$(find_application_jar)"
else
  echo "Usando el JAR existente; se omite la descarga y compilación con Gradle."
fi
[[ -n "$APP_JAR" ]] || { echo "No se encontró el JAR de Base Repo en build/libs." >&2; exit 1; }
pkill -f 'base[-_]repo\.jar' || true
nohup java -jar "$APP_JAR" --spring.config.location="file:$CONF" --spring.profiles.active=production > "$APP_DIR/base-repo.log" 2>&1 &
echo "Base Repo iniciado. Log: $APP_DIR/base-repo.log"
echo "Entregue $APP_DIR/haproxy-base-repo.cfg al administrador del HAProxy remoto."
