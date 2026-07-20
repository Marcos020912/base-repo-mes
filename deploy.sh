#!/usr/bin/env bash
set -euo pipefail

# Interactive deployment for Debian/Ubuntu. Run from the project directory.
if [[ $EUID -ne 0 ]]; then echo "Ejecute con sudo: sudo ./deploy.sh"; exit 1; fi
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
CONF="$APP_DIR/config/application.properties"
ask(){ local var=$1 prompt=$2 default=${3:-}; read -r -p "$prompt${default:+ [$default]}: " value; printf -v "$var" '%s' "${value:-$default}"; }
install_if_missing(){ command -v "$1" >/dev/null 2>&1 || { apt-get update; apt-get install -y "$2"; }; }
# Obtiene el último valor de una clave YAML simple sin interpretar la clave como una expresión regular.
property_value(){ awk -v key="$1" 'index($0, key ":") == 1 { value=substr($0, length(key)+2); sub(/^[[:space:]]+/, "", value) } END { print value }' "$CONF" 2>/dev/null; }
install_elasticsearch(){
  if systemctl list-unit-files 2>/dev/null | grep -q '^elasticsearch.service'; then return; fi
  echo "Instalando Elasticsearch desde el repositorio oficial de Elastic…"
  apt-get update
  apt-get install -y ca-certificates curl gnupg
  install -d -m 0755 /usr/share/keyrings
  curl -fsSL https://artifacts.elastic.co/GPG-KEY-elasticsearch | gpg --dearmor -o /usr/share/keyrings/elasticsearch-keyring.gpg
  chmod 0644 /usr/share/keyrings/elasticsearch-keyring.gpg
  echo 'deb [signed-by=/usr/share/keyrings/elasticsearch-keyring.gpg] https://artifacts.elastic.co/packages/9.x/apt stable main' > /etc/apt/sources.list.d/elastic-9.x.list
  apt-get update
  apt-get install -y elasticsearch
  cat > /etc/elasticsearch/elasticsearch.yml <<'EOF'
cluster.name: base-repo
node.name: base-repo-node
discovery.type: single-node
network.host: 127.0.0.1
http.port: 9200
xpack.security.enabled: false
xpack.security.http.ssl.enabled: false
xpack.security.transport.ssl.enabled: false
EOF
  install -d -m 0755 /etc/elasticsearch/jvm.options.d
  printf '%s\n' '-Xms1g' '-Xmx1g' > /etc/elasticsearch/jvm.options.d/base-repo.options
}

install_if_missing java openjdk-21-jdk
install_if_missing psql postgresql
if ! command -v curl >/dev/null; then apt-get update; apt-get install -y curl ca-certificates gnupg; fi

install_elasticsearch
systemctl enable --now postgresql
systemctl enable --now elasticsearch
for _ in $(seq 1 30); do curl -fsS http://localhost:9200 >/dev/null 2>&1 && break; sleep 1; done
curl -fsS http://localhost:9200 >/dev/null || { echo "Elasticsearch no pudo iniciar. Revise: journalctl -u elasticsearch"; exit 1; }

REUSE_CONFIGURATION="N"
if grep -q '^# Managed by deploy.sh' "$CONF" 2>/dev/null; then
  ask REUSE_CONFIGURATION "Se detectó una configuración previa. ¿Reutilizarla sin volver a pedir datos? (S/n)" "S"
fi
if [[ "${REUSE_CONFIGURATION,,}" == "s" || "${REUSE_CONFIGURATION,,}" == "si" || "${REUSE_CONFIGURATION,,}" == "sí" ]]; then
  APP_PORT="$(property_value 'server.port')"; APP_DOMAIN="$(property_value 'repo.public-domain')"; APP_BIND="$(property_value 'server.address')"
  DB_NAME="$(property_value 'repo.deploy.db-name')"; DB_USER="$(property_value 'spring.datasource.username')"; DB_PASSWORD="$(property_value 'spring.datasource.password')"
  MAIL_DESCRIPTION="$(property_value 'repo.mail.description')"; MAIL_HOST="$(property_value 'spring.mail.host')"; MAIL_PORT="$(property_value 'spring.mail.port')"; MAIL_USER="$(property_value 'spring.mail.username')"; MAIL_PASSWORD="$(property_value 'spring.mail.password')"; ES_URL="$(property_value 'repo.search.url')"
  HAPROXY_NETWORK="$(property_value 'repo.deploy.haproxy-network')"; APP_PRIVATE_HOST="$(property_value 'repo.deploy.private-host')"; HAPROXY_FRONTEND_PORT="$(property_value 'repo.deploy.haproxy-port')"; CONFIGURE_FIREWALL="N"
  # Compatibilidad con configuraciones creadas por versiones anteriores del script.
  APP_PORT=${APP_PORT:-8090}; APP_DOMAIN=${APP_DOMAIN:-localhost}; APP_BIND=${APP_BIND:-0.0.0.0}
  DB_NAME=${DB_NAME:-base_repo}; DB_USER=${DB_USER:-base_repo}
  MAIL_DESCRIPTION=${MAIL_DESCRIPTION:-webmail.mes.gob.cu}; MAIL_HOST=${MAIL_HOST:-webmail.mes.gob.ci}; MAIL_PORT=${MAIL_PORT:-25}; MAIL_USER=${MAIL_USER:-soporte@mes.gob.cu}
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
  ask ES_URL "URL de Elasticsearch" "$(property_value 'repo.search.url')"; ES_URL=${ES_URL:-http://localhost:9200}
  ask CONFIGURE_FIREWALL "¿Configurar UFW para que solo HAProxy acceda al puerto de la app? (s/N)" "N"
fi

sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'" | grep -q 1 || sudo -u postgres psql -c "CREATE USER \"$DB_USER\" WITH PASSWORD '$DB_PASSWORD';"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'" | grep -q 1 || sudo -u postgres createdb -O "$DB_USER" "$DB_NAME"

cp "$CONF" "$CONF.bak.$(date +%s)"
cat >> "$CONF" <<EOF

# Managed by deploy.sh
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
repo.search.url: $ES_URL
repo.search.enabled: true
repo.mail.description: $MAIL_DESCRIPTION
spring.mail.host: $MAIL_HOST
spring.mail.port: $MAIL_PORT
spring.mail.username: $MAIL_USER
spring.mail.password: $MAIL_PASSWORD
repo.mail.from: $MAIL_USER
repo.allowed-origin-pattern: https://$APP_DOMAIN
repo.public-domain: $APP_DOMAIN
repo.deploy.db-name: $DB_NAME
repo.deploy.haproxy-network: $HAPROXY_NETWORK
repo.deploy.private-host: $APP_PRIVATE_HOST
repo.deploy.haproxy-port: $HAPROXY_FRONTEND_PORT
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
./gradlew --no-daemon -Dprofile=minimal bootJar
pkill -f 'base-repo.jar' || true
nohup java -jar build/libs/base-repo.jar > "$APP_DIR/base-repo.log" 2>&1 &
echo "Base Repo iniciado. Log: $APP_DIR/base-repo.log"
echo "Entregue $APP_DIR/haproxy-base-repo.cfg al administrador del HAProxy remoto."
