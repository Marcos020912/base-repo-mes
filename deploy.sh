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
chmod 600 "$CONF"
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
ask_secret(){
  local var=$1 prompt=$2 value=""
  if ! read -r -s -p "$prompt: " value </dev/tty; then
    echo "No se pudo leer '$prompt'." >&2; exit 1
  fi
  echo
  printf -v "$var" '%s' "$value"
}
install_if_missing(){ command -v "$1" >/dev/null 2>&1 || { apt-get update; apt-get install -y "$2"; }; }
# Obtiene el último valor de una clave YAML simple sin interpretar la clave como una expresión regular.
property_value(){
  [[ -r "$CONF" ]] || return 0
  awk -v key="$1" '(index($0, key ":") == 1 || index($0, key "=") == 1) { value=substr($0, length(key)+2); sub(/^[[:space:]]+/, "", value) } END { print value }' "$CONF"
}
boolean_value(){
  case "${1,,}" in s|si|sí|y|yes|true|1) printf 'true\n' ;; n|no|false|0) printf 'false\n' ;; *) printf '%s\n' "$1" ;; esac
}
remove_managed_properties(){
  local tmp keys
  keys='server.port,server.address,server.forward-headers-strategy,server.tomcat.remoteip.remote-ip-header,server.tomcat.remoteip.protocol-header,spring.datasource.driver-class-name,spring.datasource.url,spring.datasource.username,spring.datasource.password,spring.jpa.database,spring.jpa.database-platform,repo.basepath,repo.search.url,repo.search.enabled,repo.mail.description,spring.mail.host,spring.mail.port,spring.mail.username,spring.mail.password,spring.mail.properties.mail.smtp.auth,spring.mail.properties.mail.smtp.starttls.enable,spring.mail.properties.mail.smtp.starttls.required,spring.mail.properties.mail.smtp.ssl.trust,spring.mail.properties.mail.smtp.ssl.checkserveridentity,spring.mail.properties.mail.smtp.connectiontimeout,spring.mail.properties.mail.smtp.timeout,spring.mail.properties.mail.smtp.writetimeout,repo.mail.from,repo.fixity.enabled,repo.fixity.alert-to,repo.privacy.require-assessment,repo.allowed-origin-pattern,repo.public-domain,repo.deploy.db-name,repo.deploy.haproxy-network,repo.deploy.private-host,repo.deploy.haproxy-port,repo.auth.enabled,repo.auth.jwtSecret,repo.auth.bootstrap-admin-password,repo.datacite.enabled,repo.datacite.api-url,repo.datacite.repository-id,repo.datacite.password,repo.datacite.prefix,repo.datacite.public-base-url,repo.scientific.orcid.enabled,repo.scientific.orcid.environment,repo.scientific.orcid.client-id,repo.scientific.orcid.client-secret,repo.scientific.orcid.redirect-uri'
  tmp="$(mktemp "$CONF.XXXXXX")"
  awk -v keys="$keys" '
    BEGIN { count=split(keys, items, ","); for (i=1; i<=count; i++) managed[items[i]]=1 }
    /^[[:space:]]*#/ { print; next }
    {
      line=$0; sub(/^[[:space:]]+/, "", line); separator=index(line, ":"); eq=index(line, "=")
      if (eq > 0 && (separator == 0 || eq < separator)) separator=eq
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
ask SETUP_POSTGRES "¿Instalar y configurar PostgreSQL? (s/N)" "N"
ask SETUP_ELASTIC "¿Configurar Elasticsearch? (s/N)" "N"
if [[ "$(boolean_value "$SETUP_POSTGRES")" == true ]]; then
  install_if_missing psql postgresql
  systemctl enable --now postgresql
fi
install_if_missing curl curl

if [[ "$(boolean_value "$SETUP_ELASTIC")" == true ]]; then
configure_elasticsearch_tar
for _ in $(seq 1 60); do curl -fsS http://localhost:9200 >/dev/null 2>&1 && break; sleep 1; done
curl -fsS http://localhost:9200 >/dev/null || { echo "Elasticsearch no pudo iniciar. Revise: journalctl -u base-repo-elasticsearch -n 100 --no-pager"; exit 1; }
fi

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
  FIXITY_ENABLED="$(boolean_value "$(property_value 'repo.fixity.enabled')")"
  FIXITY_ENABLED=${FIXITY_ENABLED:-false}
  PRIVACY_REQUIRED="$(boolean_value "$(property_value 'repo.privacy.require-assessment')")"; PRIVACY_REQUIRED=${PRIVACY_REQUIRED:-false}
  FIXITY_ALERT_TO="$(property_value 'repo.fixity.alert-to')"
  ES_URL=${ES_URL:-http://localhost:9200}; APP_PRIVATE_HOST=${APP_PRIVATE_HOST:-$(hostname -I | awk '{print $1}')}; HAPROXY_FRONTEND_PORT=${HAPROXY_FRONTEND_PORT:-443}
  if [[ -z "$DB_PASSWORD" || -z "$MAIL_PASSWORD" ]]; then
    echo "La configuración anterior no contiene todas las credenciales; se solicitarán para completar la actualización."
    [[ -n "$DB_PASSWORD" ]] || ask DB_PASSWORD "Contraseña PostgreSQL"
    [[ -n "$MAIL_PASSWORD" ]] || ask MAIL_PASSWORD "Contraseña SMTP"
  fi
  echo "Reutilizando la configuración existente para $APP_DOMAIN."
  if [[ "$(property_value 'repo.auth.bootstrap-admin-password')" == 'admin12345' ]]; then
    echo "ADVERTENCIA: la clave inicial conocida no se cambiará en la base de datos. Use Mi cuenta para rotarla." >&2
  fi
  if [[ -z "$(property_value 'repo.auth.jwtSecret')" || "$(property_value 'repo.auth.jwtSecret')" == 'vkfvoswsohwrxgjaxipuiyyjgubggzdaqrcuupbugxtnalhiegkppdgjgwxsmvdb' ]]; then
    echo "La clave JWT no es segura. Vuelva a ejecutar y responda N a reutilizar para generar una única." >&2
    exit 1
  fi
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
  ask_secret DB_PASSWORD "Contraseña PostgreSQL (vacío = conservar anterior)"
  DB_PASSWORD=${DB_PASSWORD:-$EXISTING_DB_PASSWORD}
  ask MAIL_DESCRIPTION "Descripción del servidor de correo" "$(property_value 'repo.mail.description')"; MAIL_DESCRIPTION=${MAIL_DESCRIPTION:-webmail.mes.gob.cu}
  ask MAIL_HOST "Servidor SMTP" "$(property_value 'spring.mail.host')"; MAIL_HOST=${MAIL_HOST:-webmail.mes.gob.ci}
  ask MAIL_PORT "Puerto SMTP" "$(property_value 'spring.mail.port')"; MAIL_PORT=${MAIL_PORT:-25}
  ask MAIL_USER "Usuario SMTP" "$(property_value 'spring.mail.username')"; MAIL_USER=${MAIL_USER:-soporte@mes.gob.cu}
  EXISTING_MAIL_PASSWORD="$(property_value 'spring.mail.password')"
  ask_secret MAIL_PASSWORD "Contraseña SMTP (vacío = conservar anterior)"
  MAIL_PASSWORD=${MAIL_PASSWORD:-$EXISTING_MAIL_PASSWORD}
  ask MAIL_STARTTLS "¿El SMTP usa STARTTLS? (S/n)" "$(property_value 'spring.mail.properties.mail.smtp.starttls.enable')"; MAIL_STARTTLS=${MAIL_STARTTLS:-true}
  MAIL_STARTTLS="$(boolean_value "$MAIL_STARTTLS")"
  ask FIXITY_ENABLED "¿Activar auditoría semanal de integridad? (s/N)" "$(property_value 'repo.fixity.enabled')"; FIXITY_ENABLED="$(boolean_value "${FIXITY_ENABLED:-false}")"
  ask FIXITY_ALERT_TO "Correo para alertas de integridad (vacío = conservar; - = desactivar)" "$(property_value 'repo.fixity.alert-to')"
  [[ "$FIXITY_ALERT_TO" == '-' ]] && FIXITY_ALERT_TO=""
  [[ -z "$FIXITY_ALERT_TO" || "$FIXITY_ALERT_TO" =~ ^[^[:space:]@]+@[^[:space:]@]+$ ]] || { echo "Correo de alertas no válido." >&2; exit 1; }
  ask PRIVACY_REQUIRED "¿Exigir evaluación de privacidad antes de enviar a revisión? (s/N)" "$(property_value 'repo.privacy.require-assessment')"; PRIVACY_REQUIRED="$(boolean_value "${PRIVACY_REQUIRED:-false}")"
  [[ "$PRIVACY_REQUIRED" == true || "$PRIVACY_REQUIRED" == false ]] || { echo "Responda s o n para la política de privacidad." >&2; exit 1; }
  ask DATACITE_ENABLED "¿Configurar integración DOI con DataCite? (s/N)" "$(property_value 'repo.datacite.enabled')"; DATACITE_ENABLED="$(boolean_value "${DATACITE_ENABLED:-false}")"
  DATACITE_API_URL="$(property_value 'repo.datacite.api-url')"; DATACITE_REPOSITORY_ID="$(property_value 'repo.datacite.repository-id')"; DATACITE_PREFIX="$(property_value 'repo.datacite.prefix')"; DATACITE_PASSWORD="$(property_value 'repo.datacite.password')"
  if [[ "$DATACITE_ENABLED" == true ]]; then
    DATACITE_ENV_DEFAULT=test
    [[ "$DATACITE_API_URL" == 'https://api.datacite.org' ]] && DATACITE_ENV_DEFAULT=production
    ask DATACITE_ENV "Entorno DataCite (test/production)" "$DATACITE_ENV_DEFAULT"
    case "$DATACITE_ENV" in
      test) DATACITE_API_URL=https://api.test.datacite.org ;;
      production)
        ask CONFIRM_DATACITE_PRODUCTION "DataCite Production crea DOI permanentes. ¿Confirmar uso en producción? (s/N)" "N"
        [[ "$(boolean_value "$CONFIRM_DATACITE_PRODUCTION")" == true ]] || { echo "Se canceló la activación de DataCite Production." >&2; exit 1; }
        DATACITE_API_URL=https://api.datacite.org ;;
      *) echo "Entorno DataCite no válido." >&2; exit 1 ;;
    esac
    ask DATACITE_REPOSITORY_ID "ID de cuenta Repository DataCite" "$DATACITE_REPOSITORY_ID"
    ask DATACITE_PREFIX "Prefijo DOI asignado" "$DATACITE_PREFIX"
    EXISTING_DATACITE_PASSWORD="$DATACITE_PASSWORD"
    ask_secret DATACITE_PASSWORD "Contraseña/API key DataCite (vacío = conservar anterior)"
    DATACITE_PASSWORD=${DATACITE_PASSWORD:-$EXISTING_DATACITE_PASSWORD}
    [[ -n "$DATACITE_REPOSITORY_ID" && -n "$DATACITE_PASSWORD" && "$DATACITE_PREFIX" =~ ^10\.[0-9]{4,9}$ ]] || { echo "Faltan datos válidos de DataCite." >&2; exit 1; }
  fi
  DATACITE_API_URL=${DATACITE_API_URL:-https://api.test.datacite.org}
  ask ORCID_ENABLED "¿Configurar autenticación ORCID? (s/N)" "$(property_value 'repo.scientific.orcid.enabled')"; ORCID_ENABLED="$(boolean_value "${ORCID_ENABLED:-false}")"
  ORCID_ENVIRONMENT="$(property_value 'repo.scientific.orcid.environment')"; ORCID_ENVIRONMENT=${ORCID_ENVIRONMENT:-sandbox}
  ORCID_CLIENT_ID="$(property_value 'repo.scientific.orcid.client-id')"
  ORCID_CLIENT_SECRET="$(property_value 'repo.scientific.orcid.client-secret')"
  ORCID_REDIRECT_URI="$(property_value 'repo.scientific.orcid.redirect-uri')"
  if [[ "$ORCID_ENABLED" == true ]]; then
    ask ORCID_ENVIRONMENT "Entorno ORCID (sandbox/production)" "$ORCID_ENVIRONMENT"
    [[ "$ORCID_ENVIRONMENT" == sandbox || "$ORCID_ENVIRONMENT" == production ]] || { echo "Entorno ORCID no válido." >&2; exit 1; }
    ask ORCID_CLIENT_ID "Client ID de ORCID" "$ORCID_CLIENT_ID"
    ask_secret NEW_ORCID_CLIENT_SECRET "Client secret ORCID (vacío = conservar anterior)"
    ORCID_CLIENT_SECRET=${NEW_ORCID_CLIENT_SECRET:-$ORCID_CLIENT_SECRET}
    ask ORCID_REDIRECT_URI "URL de retorno ORCID registrada en ORCID" "${ORCID_REDIRECT_URI:-https://$APP_DOMAIN$( [[ "$HAPROXY_FRONTEND_PORT" == 443 ]] || printf ':%s' "$HAPROXY_FRONTEND_PORT" )/api/v1/scientific/orcid/callback}"
    [[ -n "$ORCID_CLIENT_ID" && -n "$ORCID_CLIENT_SECRET" && "$ORCID_REDIRECT_URI" == https://*/api/v1/scientific/orcid/callback ]] || { echo "Faltan datos válidos de ORCID; se requiere HTTPS público." >&2; exit 1; }
  fi
  ask ES_URL "URL de Elasticsearch" "$(property_value 'repo.search.url')"; ES_URL=${ES_URL:-http://localhost:9200}
  ask CONFIGURE_FIREWALL "¿Configurar UFW para que solo HAProxy acceda al puerto de la app? (s/N)" "N"
  APP_JWT_SECRET="$(property_value 'repo.auth.jwtSecret')"
  if [[ -z "$APP_JWT_SECRET" || "$APP_JWT_SECRET" == 'vkfvoswsohwrxgjaxipuiyyjgubggzdaqrcuupbugxtnalhiegkppdgjgwxsmvdb' ]]; then
    install_if_missing openssl openssl
    APP_JWT_SECRET="$(openssl rand -hex 48)"
    echo "Se generó una clave JWT única para esta instalación. Las sesiones anteriores quedarán invalidadas."
  fi
  BOOTSTRAP_ADMIN_PASSWORD="$(property_value 'repo.auth.bootstrap-admin-password')"
  if [[ -z "$BOOTSTRAP_ADMIN_PASSWORD" || "$BOOTSTRAP_ADMIN_PASSWORD" == 'admin12345' ]]; then
    ask_secret BOOTSTRAP_ADMIN_PASSWORD "Contraseña inicial única del administrador (solo para crear una cuenta nueva, mínimo 12 caracteres)"
    [[ ${#BOOTSTRAP_ADMIN_PASSWORD} -ge 12 ]] || { echo "La contraseña inicial debe tener al menos 12 caracteres." >&2; exit 1; }
  fi
fi

if [[ "$(boolean_value "$SETUP_POSTGRES")" == true ]]; then
sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'" | grep -q 1 || sudo -u postgres psql -c "CREATE USER \"$DB_USER\" WITH PASSWORD '$DB_PASSWORD';"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1 || sudo -u postgres createdb -O "$DB_USER" "$DB_NAME"
fi

if [[ "$(boolean_value "$REUSE_CONFIGURATION")" != true ]]; then
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
repo.auth.enabled: true
repo.auth.jwtSecret: $APP_JWT_SECRET
repo.auth.bootstrap-admin-password: $BOOTSTRAP_ADMIN_PASSWORD
repo.datacite.enabled: $DATACITE_ENABLED
repo.datacite.api-url: $DATACITE_API_URL
repo.datacite.repository-id: $DATACITE_REPOSITORY_ID
repo.datacite.password: $DATACITE_PASSWORD
repo.datacite.prefix: $DATACITE_PREFIX
repo.datacite.public-base-url: https://$APP_DOMAIN$( [[ "$HAPROXY_FRONTEND_PORT" == 443 ]] || printf ':%s' "$HAPROXY_FRONTEND_PORT" )
repo.scientific.orcid.enabled: $ORCID_ENABLED
repo.scientific.orcid.environment: $ORCID_ENVIRONMENT
repo.scientific.orcid.client-id: $ORCID_CLIENT_ID
repo.scientific.orcid.client-secret: $ORCID_CLIENT_SECRET
repo.scientific.orcid.redirect-uri: $ORCID_REDIRECT_URI
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
spring.mail.properties.mail.smtp.connectiontimeout: 10000
spring.mail.properties.mail.smtp.timeout: 10000
spring.mail.properties.mail.smtp.writetimeout: 10000
repo.mail.from: $MAIL_USER
repo.fixity.enabled: $FIXITY_ENABLED
repo.fixity.alert-to: $FIXITY_ALERT_TO
repo.privacy.require-assessment: $PRIVACY_REQUIRED
repo.allowed-origin-pattern: https://$APP_DOMAIN
repo.public-domain: $APP_DOMAIN
repo.deploy.db-name: $DB_NAME
repo.deploy.haproxy-network: $HAPROXY_NETWORK
repo.deploy.private-host: $APP_PRIVATE_HOST
repo.deploy.haproxy-port: $HAPROXY_FRONTEND_PORT
# END Managed by deploy.sh
EOF
chmod 600 "$CONF"

# This file is copied to the remote HAProxy administrator; it is not applied locally.
cat > "$APP_DIR/haproxy-base-repo.cfg" <<EOF
# Añadir en el HAProxy remoto (TLS se termina en el frontend HTTPS).
backend base_repo_backend
    # Overwrite client-supplied forwarding headers before Spring consumes them.
    http-request del-header Forwarded
    http-request set-header X-Forwarded-For %[src]
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

fi # Conservar configuración y firewall intactos al reutilizar.

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
