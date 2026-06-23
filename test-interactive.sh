#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Interactive smoke test for java-service-archetype.
#
# Installs the archetype to the local Maven repo, then runs
# mvn archetype:generate in interactive mode by piping answers to each prompt
# via stdin.  The generated project is written to a temp directory and removed
# on exit.
#
# Prerequisites: mvn (system Maven) must be on PATH for the generate step.
# Usage: ./test-interactive.sh [--verify]
#   --verify  also run `mvn verify -f parent/pom.xml` on the generated project
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

ARCHETYPE_GROUP_ID="io.github.jeffjensen"
ARCHETYPE_ARTIFACT_ID="java-service-archetype"
# ARCHETYPE_VERSION is read from the project pom.xml at runtime (after install),
# so this script needs no edits when the project version changes.
RUN_VERIFY=false

for arg in "$@"; do
  case "$arg" in
    --verify) RUN_VERIFY=true ;;
    *) echo "Unknown argument: $arg" >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$(mktemp -d)"
trap 'echo "==> Cleaning up $OUT_DIR"; rm -rf "$OUT_DIR"' EXIT

# ── 1. Install ─────────────────────────────────────────────────────────────
echo "==> Installing archetype to local repo..."
(cd "$SCRIPT_DIR" && ./mvnw install -q)

# ── Read the archetype version straight from the project POM, so this script
#    needs no maintenance when the project version changes ──────────────────
ARCHETYPE_VERSION="$(cd "$SCRIPT_DIR" && ./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout --no-transfer-progress)"
echo "==> Using archetype version: $ARCHETYPE_VERSION"

# ── 2. Generate ────────────────────────────────────────────────────────────
echo "==> Generating project interactively in $OUT_DIR..."
cd "$OUT_DIR"

# Answers fed to each Maven prompt, in the exact order maven-archetype-plugin
# 3.4.x prompts. Required properties WITHOUT a non-empty default are prompted
# first, in archetype-metadata.xml order, THEN groupId / artifactId / package.
# Properties with a non-empty default are NOT prompted, so they get no answer
# line here: version (1.0.0-SNAPSHOT), serviceAreas (",") and presentationTypes
# (rest). The "," default makes serviceAreas resolve to a single "service" module.
#
#   integrations  → database:main
#   groupId       → com.example
#   artifactId    → my-service
#   package       → (Enter: accept default derived from groupId)
#   confirm       → Y
printf '%s\n' \
  "database:main" \
  "com.example" \
  "my-service" \
  "" \
  "Y" \
| mvn archetype:generate \
    --no-transfer-progress \
    -DarchetypeGroupId="$ARCHETYPE_GROUP_ID" \
    -DarchetypeArtifactId="$ARCHETYPE_ARTIFACT_ID" \
    -DarchetypeVersion="$ARCHETYPE_VERSION"

# ── 3. Show generated layout ───────────────────────────────────────────────
echo ""
echo "==> Generated project layout:"
find "my-service" -type f | sort

# ── 4. Optionally verify the generated project compiles and tests pass ─────
if [ "$RUN_VERIFY" = "true" ]; then
  echo ""
  echo "==> Running mvn verify on generated project..."
  (cd "my-service" && mvn verify -f parent/pom.xml --no-transfer-progress)
  echo "==> Build: PASSED"
fi

echo ""
echo "==> SUCCESS"
