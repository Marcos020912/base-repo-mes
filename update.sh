#!/usr/bin/env bash
# Actualización manual controlada. No instala ni configura PostgreSQL/Elastic.
set -euo pipefail
umask 077
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
[[ $# == 3 ]] || { echo "Uso: sudo ./update.sh vX.Y.Z /ruta/base-repo.jar SHA256_RELEASE"; exit 1; }
TAG=$1
[[ "$TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Se requiere un tag estable vX.Y.Z"; exit 1; }
ARTIFACT="$(realpath "$2")"
[[ -s "$ARTIFACT" && -s config/application.properties ]] || { echo "Falta JAR o configuración"; exit 1; }
command -v curl >/dev/null
command -v python3 >/dev/null
command -v ss >/dev/null
# Fail before network, checkout or process termination if artifact is not approved.
python3 "$ROOT/tools/releases/verify_artifact.py" "$ARTIFACT" "$3"
# URL configurable si el servidor no escucha en loopback o usa otro puerto.
URL="${APP_CHECK_URL:-http://127.0.0.1:8090/login.html}"
CHECK_PORT="$(python3 - "$URL" <<'PYPORT'
import sys,urllib.parse
url=urllib.parse.urlsplit(sys.argv[1])
if url.scheme!='http' or not url.hostname or url.username or url.password or url.query or url.fragment:
    raise SystemExit('APP_CHECK_URL debe ser HTTP interno sin credenciales/query/fragmento.')
print(url.port or 80)
PYPORT
)"
exec 9>"$ROOT/.update.lock"
flock -n 9 || { echo "Ya hay una actualización en curso"; exit 1; }
[[ -z "$(git status --porcelain --untracked-files=no)" ]] || { echo "Hay cambios locales versionados. Guárdelos antes."; exit 1; }
# Solo descarga código; no ejecuta el despliegue ni compila en la VM.
git fetch https://github.com/Marcos020912/base-repo-mes.git "refs/tags/$TAG"
COMMIT="$(git rev-parse FETCH_HEAD)"
python3 - "$ARTIFACT" <<'PY'
import sys,zipfile
with zipfile.ZipFile(sys.argv[1]) as z:
    assert 'META-INF/MANIFEST.MF' in z.namelist()
    assert any(n.startswith('BOOT-INF/classes/') for n in z.namelist()), 'No es un bootJar'
PY
echo "Versión: $TAG ($COMMIT)"
echo "El JAR debe haberse construido desde ese tag y verificado con el SHA-256 de su Release."
echo "Antes de continuar: respaldo comprobado de PostgreSQL y archivos; ventana de mantenimiento."
read -r -p "¿Respaldo y procedencia del JAR confirmados? Escriba SI: " CONFIRM </dev/tty
[[ "$CONFIRM" == SI ]] || exit 1
OLD=""
for f in build/libs/base-repo.jar build/libs/base_repo.jar; do
  [[ -s "$f" ]] && { OLD=$f; break; }
done
[[ -n "$OLD" ]] || { echo "No hay JAR anterior para respaldo"; exit 1; }
BACKUP="$ROOT/.releases/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"
cp -p config/application.properties "$BACKUP/application.properties"
cp "$OLD" "$BACKUP/previous.jar"
cp "$ARTIFACT" "$BACKUP/candidate.jar"
git rev-parse HEAD > "$BACKUP/previous-commit"
sha256sum "$BACKUP/candidate.jar"
# Identifica solo procesos de esta instalación; no usa pkill global.
PIDS="$(python3 - "$ROOT" <<'PY'
import pathlib,sys,os
root=sys.argv[1]
for d in pathlib.Path('/proc').glob('[0-9]*'):
    try:
        args=(d/'cmdline').read_bytes().split(b'\0')
        if b'-jar' not in args: continue
        jar=os.fsdecode(args[args.index(b'-jar')+1])
        cwd=os.readlink(d/'cwd')
        path=os.path.realpath(os.path.join(cwd,jar))
        if path in [root+'/build/libs/base-repo.jar',root+'/build/libs/base_repo.jar']:
            print(d.name)
    except (OSError,IndexError): pass
PY
)"
for pid in $PIDS; do
  kill -TERM "$pid"
  for _ in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  if kill -0 "$pid" 2>/dev/null; then echo "El proceso no terminó; abortando"; exit 1; fi
done
git checkout --detach "$COMMIT"
cp "$BACKUP/candidate.jar" "$OLD.new"
mv "$OLD.new" "$OLD"
nohup java -jar "$ROOT/$OLD" --spring.config.location="file:$ROOT/config/application.properties" --spring.profiles.active=production > "$ROOT/base-repo.log" 2>&1 &
PID=$!
echo "$PID" > "$BACKUP/new-pid"
candidate_listens(){
  ss -ltnp "sport = :$CHECK_PORT" 2>/dev/null | grep -Eq "pid=${PID}(,|\))"
}
for _ in $(seq 1 90); do
  if ! kill -0 "$PID" 2>/dev/null; then break; fi
  if candidate_listens && [[ "$(curl --noproxy '*' -s -o /dev/null -w '%{http_code}' --max-time 3 "$URL" || true)" == 200 ]]; then
    echo "Actualizado a $TAG. Login disponible. Respaldo: $BACKUP"
    echo "Verifique además registro, búsquedas y descarga de archivos."
    exit 0
  fi
  sleep 2
done
echo "No se confirmó el arranque. Revise base-repo.log. Respaldo: $BACKUP" >&2
echo "No se revierte la base automáticamente: un cambio de esquema puede ser incompatible." >&2
exit 1
