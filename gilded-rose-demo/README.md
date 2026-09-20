# Gilded Rose → Chain of Responsibility (MCP-driven, Parallel Change)

A reproducible demo of migrating the Gilded Rose kata into a chain of
responsibility via [Parallel Change](https://martinfowler.com/bliki/ParallelChange.html),
driven entirely by the opencode CLI / MCP against this project's own
refactoring engine.

## What you can watch

| Cast | What it shows |
|------|---------------|
| `cast/gilded-rose-opencode-opencode-big-pickle.cast` | An opencode agent session. The agent reads `AGENTS.md`, then performs the full Parallel Change: **Parallel Implementation** (M0: new `ItemUpdater`/… classes, new, unused) → **Expand** (M1: introduce polymorphic dispatch while keeping the old path) → **Contract Shift** (M2) → **Remove Waste** (M3: delete old branches, move `sulfuras`/`aged brie`/etc. into their updaters). All 13 golden-master tests stay green after every milestone. |
| `cast/gilded-rose-scripted.cast` | The deterministic rehearsal: `scripts/refactor_gilded_rose.py` talks to the running MCP server (`scripts/mcp_client.py`), walks the same milestone plan, and re-runs `mvn test` between moves. Same 13 tests green. |

Play either with `asciinema play cast/<name>.cast` or upload with
`asciinema upload cast/<name>.cast`.

## Directory layout

```
scripts/
  mcp_client.py            # minimal typed MCP stdio client (invokes tools)
  refactor_gilded_rose.py  # deterministic, step-by-step Parallel Change driver
  record-demo.sh           # records a cast from the opencode agent OR the script
cast/                      # the recorded asciicasts above
opencode.json              # MCP server config (refactor-mcp over stdio)
AGENTS.md                  # directives the agent follows during the migration
src/                       # starts pristine; the migration rewrites it
```

## Prerequisites

- `opencode` CLI, `asciinema`, Java 21, Maven
- The refactoring engine MCP server fat jar:
  `/workspace/refactor-mcp/target/refactor-mcp-0.1.0-SNAPSHOT-fat.jar`
  (build with `mvn package -DskipTests` in `/workspace`)

## Re-run the migration from a clean slate

```sh
# 1. restore the pristine kata
git checkout -- src
rm -f src/main/java/gildedrose/ItemUpdater.java src/main/java/gildedrose/*Updater.java

# 2a. deterministic (scripted): runs the milestone plan through the MCP server
python3 scripts/refactor_gilded_rose.py

# 2b. agent-driven (interactive): let opencode read AGENTS.md and drive
opencode run -m opencode/big-pickle \
  "Follow AGENTS.md completely: transform the Gilded Rose into a chain of
   responsibility via Parallel Change (Milestones 0-3). Narrate each move,
   run the full test suite after every milestone, and finish with all
   13 tests green."
```

After either path: `src/main/java/gildedrose/GildedRose.java` delegates to
`ItemUpdater` + one class per item family, and `mvn test` reports 13 green.

## Record a fresh cast

```sh
bash scripts/record-demo.sh            # agent session
bash scripts/record-demo.sh --scripted # deterministic MCP run
```