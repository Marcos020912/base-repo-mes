#!/usr/bin/env bash
# Build an immutable local candidate without overwriting a running demo's JAR.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
[[ -z "$(git status --porcelain)" ]] || { echo "Commit/review changes before packaging a candidate." >&2; exit 1; }
# Gradle 8.12.1/project runtime uses JDK21; do not change the system default.
if [[ -z "${JAVA_HOME:-}" && -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java ]]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
fi
JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAVA_VERSION="$("$JAVA_BIN" -version 2>&1)"
JDK21_PATTERN='version "21([."]|$)'
[[ "$JAVA_VERSION" =~ $JDK21_PATTERN ]] || {
  echo "Se requiere JDK21. Configure JAVA_HOME con su instalación de Java21." >&2
  exit 1
}
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
COMMIT="$(git rev-parse HEAD)"
OUTPUT="$ROOT/build/releases/$COMMIT"
mkdir -p "$ROOT/build/releases"
mkdir "$OUTPUT" || { echo "Candidate exists; do not overwrite it." >&2; exit 1; }
STAGING="$(mktemp -d "$ROOT/build/candidate-stage.XXXXXX")"
trap 'rm -rf "$STAGING"' EXIT
cat > "$STAGING/stage.gradle" <<'GRADLE'
allprojects { afterEvaluate { tasks.matching { it.name == 'bootJar' }.configureEach { destinationDirectory = file(System.getProperty('baseRepo.stage')) } } }
GRADLE
echo "Compilando con JDK21. Log: $OUTPUT/build.log"
if ! ./gradlew --no-daemon -Dprofile=minimal -I "$STAGING/stage.gradle" -DbaseRepo.stage="$STAGING" bootJar > "$OUTPUT/build.log" 2>&1; then
  echo "Falló la compilación. Últimas líneas del log:" >&2
  tail -n 40 "$OUTPUT/build.log" >&2
  exit 1
fi
cp "$STAGING/base-repo.jar" "$OUTPUT/base-repo.jar"
(cd "$OUTPUT" && sha256sum base-repo.jar > SHA256SUMS)
DIGEST="$(cut -d' ' -f1 "$OUTPUT/SHA256SUMS")"
python3 tools/releases/verify_artifact.py "$OUTPUT/base-repo.jar" "$DIGEST" > "$OUTPUT/artifact-audit.json"
python3 - "$COMMIT" "$DIGEST" "$OUTPUT" <<'PY'
import json,sys,datetime
from pathlib import Path
commit,digest,output=sys.argv[1:]
Path(output,'candidate.json').write_text(json.dumps({'status':'candidate-not-production-approved','commit':commit,'sha256':digest,'built_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat()},indent=2)+'\n')
PY
cp docs/PRODUCTION_RUNBOOK.md "$OUTPUT/PRODUCTION_RUNBOOK.md"
echo "Candidate: $OUTPUT"
echo "No tag, upload, merge or deployment performed. Run acceptance before release."
