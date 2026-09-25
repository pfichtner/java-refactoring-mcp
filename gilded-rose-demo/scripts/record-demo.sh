#!/usr/bin/env bash
# Record the Gilded Rose -> Chain of Responsibility demo as an asciinema cast.
#
#   scripts/record-demo.sh                      # opencode agent session (headline demo)
#   scripts/record-demo.sh --scripted           # deterministic MCP-driven run (rehearsal/backup)
#
# Output lands in cast/<name>.cast. Requires: asciinema, java 21, and the
# java-refactoring fat jar built at /workspace/refactor-mcp/target.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CAST_DIR="$ROOT/cast"
JAR=/workspace/refactor-mcp/target/refactor-mcp-0.1.3-SNAPSHOT-fat.jar

[ -f "$JAR" ] || { echo "fat jar missing — build it first (mvn package -DskipTests in /workspace)"; exit 1; }

PROMPT=${PROMPT:-"Follow AGENTS.md completely: transform the Gilded Rose into a chain of responsibility via Parallel Change (Milestones 0-3). Narrate each move, run the full test suite after every milestone, and finish with all 13 tests green."}
MODEL=${MODEL:-opencode/big-pickle}

mkdir -p "$CAST_DIR"
cd "$ROOT"

# Always start from the pristine kata so the cast tells one clean story.
if git rev-parse --show-toplevel >/dev/null 2>&1; then
  BASELINE_REF=${BASELINE_REF:-$(git log --reverse --diff-filter=A --format=%H -- src/main/java/gildedrose/GildedRose.java)}
  [ -n "$BASELINE_REF" ] || { echo "could not locate the pristine Gilded Rose source"; exit 1; }
  git restore --source="$BASELINE_REF" -- src
fi

case "${1:-}" in
  --scripted)
    CAST="$CAST_DIR/gilded-rose-scripted.cast"
    echo "Recording deterministic MCP-driven migration -> $CAST"
    asciinema rec "$CAST" --rows 40 --cols 120 --overwrite \
      --command "python3 scripts/refactor_gilded_rose.py"
    ;;
  *)
    SAFE_MODEL="${MODEL//\//-}"
    CAST="$CAST_DIR/gilded-rose-opencode-$SAFE_MODEL.cast"
    echo "Recording opencode agent session ($MODEL) -> $CAST"
    asciinema rec "$CAST" --rows 40 --cols 120 --overwrite \
      --command "NO_COLOR=1 opencode run -m '$MODEL' '$PROMPT'"
    ;;
esac

echo
echo "Done. Play back with:  asciinema play $CAST"
echo "Upload to https://asciinema.org with:  asciinema upload $CAST"