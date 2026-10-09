#!/usr/bin/env bash
# Build an immutable local candidate without overwriting a running demo's JAR.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
[[ -z "$(git status --porcelain)" ]] || { echo "Commit/review changes before packaging a candidate." >&2; exit 1; }
COMMIT="$(git rev-parse HEAD)"
OUTPUT="$ROOT/build/releases/$COMMIT"
mkdir -p "$ROOT/build/releases"
mkdir "$OUTPUT" || { echo "Candidate exists; do not overwrite it." >&2; exit 1; }
STAGING="$(mktemp -d "$ROOT/build/candidate-stage.XXXXXX")"
trap 'rm -rf "$STAGING"' EXIT
cat > "$STAGING/stage.gradle" <<'GRADLE'
allprojects { afterEvaluate { tasks.matching { it.name == 'bootJar' }.configureEach { destinationDirectory = file(System.getProperty('baseRepo.stage')) } } }
GRADLE
./gradlew --no-daemon -Dprofile=minimal -I "$STAGING/stage.gradle" -DbaseRepo.stage="$STAGING" bootJar > "$OUTPUT/build.log" 2>&1
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
