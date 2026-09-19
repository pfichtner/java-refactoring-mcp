# Evaluation Plan — LLM Refactoring vs LLM + java-refactoring-mcp

Status: **ready to execute**. This document is the runnable plan that answers
the question in `EVALUATION.md`:

> Does a coding agent produce better, cheaper, faster Java refactorings when it
> uses this tool versus editing source files directly?

Everything in here was verified live in the lab before writing:

| Check | Result |
|-------|--------|
| JDK 21 (lab default) | `/opt/jdk21/bin/java` — verified `21.0.5+11-LTS` |
| JDK 17 (`/workspace` toolchain needs 21) | now built/`installed` under 21 |
| CLI fat-free jar + module classpath | `refactor-cli/target/refactor-cli-0.1.0-SNAPSHOT.jar` drives `Main` |
| MCP server (stdio) | `refactor-mcp/target/refactor-mcp-0.1.0-SNAPSHOT.jar` + its deps; handshake returns `java-refactoring` tools |
| opencode `run --format json` | emits one JSON event per step incl. `tokens.{input,output,cache}` and `cost`; free models available (`opencode/*-free`) |

---

## 0. Delivery contract

At the end, produce a single report `EVALUATION_RESULTS.md` at the repo root:

- Per-task matrix with measured **Precision / Recall / F1 / Compiles / Semantically equivalent** for **A (LLM only)** vs **B (LLM + tool)**.
- Per-task cost table: **Input tokens, Output tokens, API calls, Latency (s)**.
- Cost per strategy averaged across all cells.
- A **reproducibility note**: exact jar hashes, model name, temperature, prompt text, and harness invocation. No approval files are regenerated blindly.

---

## 1. Scope (agreed with user)

| Dimension | Choice |
|-----------|--------|
| Projects | 3 real small/medium Maven Java projects |
| Tasks | 6 refactorings per project |
| Agent driver | `opencode run --format json` (same free model both arms) |
| Approach B wiring | MCP server over stdio (the tool is a real stdio MCP server) |

### 1.1 Projects (already cloned into `/tmp/opencode/`)

| Project | Why |
|---------|-----|
| `javapoet` (square, `javapoet-1.13.0`) | small-medium, generics/lambdas, multi-package, has tests in tree |
| `commons-codec` (apache) | bigger real codebase, multi-file rename stress, test suite present |
| `commons-io` (apache) | medium real codebase, package + import churn for move/rename-pkg |

Each is a **git clone with `git reset --hard` being the rebaseline primitive**.

---

## 2. Task suite (per project)

Six cells per project, deliberately spanning the EVALUATION.md predictions
(listed where each prediction lives):

| # | Task (JDT tool) | Flagship prediction |
|---|-----------------|---------------------|
| 1 | **rename field** (declaration + all refs) | multi-file recall drop for A; FIELD on javapoet |
| 2 | **rename method** (declaration + all call sites) | multi-file recall drop for A |
| 3 | **rename field in a class using generics/lambdas** | generics/lambdas path |
| 4 | **rename method containing lambda/anon inner refs** | generics/lambda precision |
| 5 | **extract method** from a multi-statement selection | single-file; A sometimes inserts into wrong place |
| 6 | **move class to a new package / rename package** (round-robined) | multi-file + cross-file imports |

Mappings are **ground truth by construction** — the JDT engine underlying the
tool is the same semantic engine an IDE uses, so a correct run has
Precision=Recall=F1=1.0 automatically.

---

## 3. Ground truth & scoring

### 3.1 Ground truth generation

Per task:
1. `git reset --hard <baseline>` the project.
2. Record four numbers via the tool itself (offline): `ls -1` of changed files,
   `git diff --no-index` count, `grep -c 'new name'`, and the test result after
   applying. These four are the **golden set**: `G = files the engine touched`.

### 3.2 Metrics

For a result **R** (set of files rewritten) vs ground truth **G**:

```
precision = |R ∩ G| / |R|          (extra/incorrect edits)
recall    = |R ∩ G| / |G|          (missed edits)
F1        = 2*P*R / (P + R)        (F1=1.0 iff nothing missed, nothing extra)
```

- **Compiles**: `mvn -q -DskipTests compile` returns 0 after the change.
- **Semantically equivalent**: the project's own test suite passes
  (`mvn -q test`) after the change, plus, when A differs textually from G,
  we re-check a 3rd-party compile (see §5.3).

### 3.3 Cell protocol

Reproduced verbatim from EVALUATION.md:

1. `git reset --hard` the project.
2. Give the model the task (same paste for all cells).
3. Record all opencode JSON events → tokens/calls/latency.
4. Diff result vs G → Precision/Recall/F1.
5. Run `mvn test` → Compiles / Semantically equivalent.

---

## 4. Harness layout

```
/tmp/opencode/eval/
  run_cell.sh            # one cell = one task x one approach
  task.json              # task: project, file, line, col, op, new_name, prompt
  score.sh               # computes P/R/F1 from two manifests of changed files
  extract_metrics.py     # parses opencode --format json -> tokens/calls/latency
```

### 4.1 `run_cell.sh`

```bash
#!/usr/bin/env bash
# usage: run_cell.sh -p <project> -t <task.json> -c <A|B> -n <run_number>
set -euo pipefail
JAR=$(realpath refactor-cli/target/refactor-cli-0.1.0-SNAPSHOT.jar)
CP="$(realpath refactor-core/pom.xml)"  # engine needs module deps -> use mvn path
PROJ=/tmp/opencode/$PROJECT
git -C "$PROJ" reset --hard "$BASELINE"

case $C in
  A)  # Approach A: LLM edits files directly (no tool on the hook)
      opencode run --format json \
        -m "opencode/$(cat model.txt)" \
        "In $PROJ, $(jq -r .prompt task.json). Edit the Java source files directly. Change exactly what the refactoring requires and nothing else." \
      | tee -a runA_${TASK}_${RUN}.jsonl ;;
  B)  # Approach B: LLM + java-refactoring MCP server (stdio)
      opencode run --format json \
        -m "opencode/$(cat model.txt)" \
        --mcp java-refactoring \
        "In $PROJ, $(jq -r .prompt task.json). Use the java-refactoring MCP tool (list_refactorings -> analyze_refactoring dry-run -> apply_refactoring). Do NOT hand-edit files." \
      | tee -a runB_${TASK}_${RUN}.jsonl ;;
esac
```

### 4.2 MCP wiring (the real, default path)

opencode is configured (project + global) with:

```json
{
  "mcp": {
    "java-refactoring": {
      "type": "local",
      "command": ["java", "-jar", "/workspace/refactor-mcp/target/refactor-mcp-0.1.0-SNAPSHOT.jar"],
      "environment": {}
    }
  }
}
```

The server talks stdio JSON-RPC (verified: `initialize` → `tools/list` returns
`analyze_refactoring`, `apply_refactoring`, `list_refactorings`,
`rename_*`, `extract_*`, `move_class`, `rename_package`, …).

---

## 5. Confounder controls

- **Same model, same temperature** (both cells use the single free model in
  `model.txt`; temperature is opencode's default for that model in both arms).
- **Same task wording** — prompt template is shared (§4.1).
- **Same project state** — `git reset --hard` before every cell.
- **Randomise order** — shuffle cell order each pass so cache effects spread.
- **Job isolation** — every `opencode run` is a fresh subprocess, so each arm
  starts cold. Cache-read tokens are still recorded and reported separately.

### 5.1 Token / call / latency accounting

`opencode run --format json` streams one JSON doc per step. Fields consumed:

- `part.tokens.input`, `part.tokens.output`, `part.tokens.cache.*` → tokens.
- `part` entries of `type: "tool"` → one **API call** per file read/write; the
  MCP arm has ~1 `tools/call` per refactor.
- `timestamp` deltas → wall-clock **latency** from request to commit.

### 5.2 Dry-run vs commit

Both arms get the same **`--dry-run`-equivalent first**, then the commit step.
A's dry-run is its natural "show me the loop"; B's dry-run is
`analyze_refactoring` with no write. This isolates the interesting question
"does preview reduce hallucination" without changing the model.

### 5.3 Semantic equivalence on textually-different-but-correct A results

An LLM can produce output identical in behavior but different in spelling of
identifiers/whitespace. For such cells, we add a structural re-check: the
refactored project is re-built, and we assert the public symbol set changed is
exactly `G` via a JDT binding check (treat the tool's renamed symbol key
locator as the oracle). This prevents false negatives for A.

---

## 6. Execution budget

- ~6 tasks × 3 projects × 2 approaches = **~36 cells**.
- **3 runs** per cell where budget allows (mean ± std of the 4 cost metrics);
  the 36 applies become 108 if all runs complete mid-budget. Metric freeze:
  report mean ± std per cell, plus per-strategy aggregate.

Expected outcome to check against EVALUATION.md:

| Dimension | Expect A | Expect B |
|-----------|----------|----------|
| F1 multi-file rename | lower (missed files) | 1.0 |
| F1 generic/lambda | worse precision | 1.0 |
| Input tokens | >> B | low (tool reads) |
| API calls | many file tweaks | ~1 tool call |
| Latency | higher | lower |
| Compiles | sometimes broken | always |

---

## 7. Definition of Done

- [ ] 36 cells recorded (3 projects × 6 tasks × 2 arms), ≥1 run each
- [ ] P/R/F1 + Compiles + Semantically-equivalent captured per cell
- [ ] cost metrics (input/output tokens, API calls, latency) captured per cell
- [ ] `EVALUATION_RESULTS.md` written with tables + reproducibility note
- [ ] README gets an "Evaluation" line pointing at it (project convention: update
      README on milestone completion)

## 8. Lab notes (verified this session — do not re-derive)

- CLI usage: `java -cp "$(jar:$(cat cli.dep)" dev.mcp.refactor.cli.Main rename --file F --line L --column C --name X [--dry-run]` → prints changed files with full new source.
- Extract-method parser requires all four of `--start-line --start-column --end-line --end-column`, 1-based, end **exclusive**.
- MCP handshake over stdio: `initialize` → `notifications/initialized` → `tools/list` returns all tools; server name `java-refactoring-mcp`.
- `opencode run --format json` on the free model emits `{"type":"step-finish","tokens":{...},"cost":...}` and `{"type":"tool",...}` events → directly usable for the cost ledger.
- Free models present: `opencode/muse-spark-1.3-contributor-free`, `opencode/muse-spark-1.2-contributor-free`, `opencode/nemotron-*`, etc. Pin the winning one in `model.txt`.
- Build: `mvn -q package -DskipTests` at repo root produces both jars; MCP jar needs its dependency classpath (thin jar, not shaded).
