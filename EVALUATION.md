# Evaluation: LLM Refactoring vs LLM + java-refactoring-mcp

## Question

Does a coding agent produce better, cheaper, faster Java refactorings when it
uses this tool versus editing source files directly?

---

## What to Measure

### Correctness metrics (per task)

| Metric | Definition |
|--------|-----------|
| **Precision** | `renamed_correctly / total_renamed` — fraction of changes that were right |
| **Recall** | `renamed_correctly / should_have_been_renamed` — fraction of sites actually found |
| **F1** | Harmonic mean of precision and recall |
| **Compiles** | Boolean — does the result compile without errors? |
| **Semantically equivalent** | Boolean — does the refactored code behave identically? (verified by running the project's own test suite) |

Precision < 1 → false positives (changed something that shouldn't have been).  
Recall < 1 → false negatives (missed a reference).  
A tool that uses semantic binding resolution should achieve F1 = 1 on any
refactoring it supports. An LLM editing by hand will drop recall on multi-file
changes and drop precision when identifier names are ambiguous.

### Cost metrics (per task)

| Metric | Unit | Notes |
|--------|------|-------|
| **Input tokens** | tokens | Files read by the LLM |
| **Output tokens** | tokens | Files written by the LLM |
| **API calls** | count | Each file read/write is one or more calls |
| **Latency** | seconds | Wall-clock from request to committed change |

A tool call replaces many file-read + file-write API calls with one structured
invocation. Expected: LLM + tool uses far fewer tokens and calls.

### Qualitative dimensions

| Dimension | LLM only | LLM + tool |
|-----------|----------|------------|
| Must understand scope | Yes (which files are affected?) | No (tool discovers) |
| Must parse Java syntax | Yes | No |
| Can handle generics / lambdas | Unreliable | Yes (JDT bindings) |
| Works at any project scale | Degrades with size | Constant |
| Produces a dry-run preview | No (must diff manually) | Yes (`--dry-run`) |

---

## How to Measure

### Benchmark dataset

1. Take 5–10 real open-source Java projects (small to medium, e.g. from GitHub).
2. For each project define a suite of refactoring tasks, for example:
   - Rename field `x` in class `Foo` (affects N files)
   - Extract method from lines L1–L2 of `Bar.java`
   - Move class `Baz` from package `a.b` to `a.c`
3. Produce **ground truth** by performing each task in Eclipse IDE and capturing
   the resulting file set.

### Protocol

For each task × approach:

```
1. Reset the project to the original state.
2. Give the LLM (same model, same temperature) the task description.
   - Approach A: "Edit the files directly to rename X to Y."
   - Approach B: "Use java-refactor to rename X to Y."
3. Record all API calls, token counts, and wall-clock time.
4. Capture the resulting file set.
5. Diff against ground truth → compute precision, recall, F1.
6. Run the project's own test suite → record pass/fail.
```

### Scoring

```
precision = |{changed} ∩ {ground_truth_changed}| / |{changed}|
recall    = |{changed} ∩ {ground_truth_changed}| / |{ground_truth_changed}|
f1        = 2 * precision * recall / (precision + recall)
```

A task scores:
- **Full credit** if F1 = 1.0 and tests pass.
- **Partial credit** if F1 > 0 and tests pass (refactoring incomplete but not broken).
- **Zero** if tests fail (refactoring introduced a bug) or F1 = 0.

### Confounders to control

- **Same model** for both approaches (GPT-4o or Claude 3.5 Sonnet).
- **Same task description** wording.
- **Same project state** (git-reset before each run).
- **Randomise order** to avoid caching effects.
- **Measure 3 runs** per cell and report mean ± std.

---

## Expected Findings

| Dimension | Prediction |
|-----------|-----------|
| F1 for single-file rename | A ≈ B (both reliable) |
| F1 for multi-file rename | A < B (LLM misses files) |
| F1 for rename with generics/lambdas | A << B (LLM confused by erasure) |
| Input tokens | A >> B (LLM reads all files) |
| API calls | A >> B (one call per file vs one tool call) |
| Latency | A > B |
| Compiles | A < B (LLM makes syntax errors) |

The expected crossover point: refactorings that touch > 2 files favour the tool
strongly for both correctness and cost.

---

## Open Questions

- Does the tool's **dry-run** output reduce LLM hallucination by giving it a
  preview to verify before committing?
- Does the LLM's **chain-of-thought** about the refactoring help it catch edge
  cases even without the tool?
- For **extract method**, does the tool's structured output (signature, parameter
  list) give the LLM enough context to write good follow-up code?
- At what **project size** (LOC, file count) does the LLM-only approach become
  cost-prohibitive?
