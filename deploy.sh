#!/usr/bin/env bash
set -euo pipefail

# Interactive deployment for Debian/Ubuntu. Run from the project directory.
if [[ $EUID -ne 0 ]]; then echo "Ejecute con sudo: sudo ./deploy.sh"; exit 1; fi
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
CONF="$APP_DIR/config/application.properties"
ask(){ local var=$1 prompt=$2 default=${3:-}; read -r -p "$prompt${default:+ [$default]}: " value; printf -v "$var" '%s' "${value:-$default}"; }
install_if_missing(){ command -v "$1" >/dev/null 2>&1 || { apt-get update; apt-get install -y "$2"; }; }

install_if_missing java openjdk-21-jdk
install_if_missing psql postgresql
if ! command -v curl >/dev/null; then apt-get update; apt-get install -y curl ca-certificates gnupg; fi

if ! systemctl list-unit-files | grep -q '^elasticsearch.service'; then
  echo "Elasticsearch no está instalado. Instálelo desde el repositorio oficial de Elastic y vuelva a ejecutar este script."; exit 1
fi
systemctl enable --now postgresql
systemctl enable --now elasticsearch

ask APP_PORT "Puerto de Base Repo" "8090"
ask APP_DOMAIN "Dominio público (sin http)" "localhost"
ask APP_BIND "IP de escucha interna de Base Repo" "0.0.0.0"
ask HAPROXY_NETWORK "IP o red CIDR del HAProxy remoto (ej. 10.20.0.5 o 10.20.0.0/24)"
ask APP_PRIVATE_HOST "IP/DNS privado de este servidor visible por HAProxy" "$(hostname -I | awk '{print $1}')"
ask HAPROXY_FRONTEND_PORT "Puerto HTTPS público de HAProxy" "443"
ask DB_NAME "Base de datos PostgreSQL" "base_repo"
ask DB_USER "Usuario PostgreSQL" "base_repo"
ask DB_PASSWORD "Contraseña PostgreSQL"
ask MAIL_DESCRIPTION "Descripción del servidor de correo" "webmail.mes.gob.cu"
ask MAIL_HOST "Servidor SMTP" "webmail.mes.gob.ci"
ask MAIL_PORT "Puerto SMTP" "25"
ask MAIL_USER "Usuario SMTP" "soporte@mes.gob.cu"
ask MAIL_PASSWORD "Contraseña SMTP"
ask ES_URL "URL de Elasticsearch" "http://localhost:9200"
ask CONFIGURE_FIREWALL "¿Configurar UFW para que solo HAProxy acceda al puerto de la app? (s/N)" "N"

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
