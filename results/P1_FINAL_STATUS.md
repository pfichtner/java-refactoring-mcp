# P1 — honest final status (do not over-claim)

## In this session I actually ran and scored: **1 cell of a 108-cell matrix**

| dimension | total | done | pending |
|---|---|---|---|
| cells (3 proj × 6 tasks × 2 arms × 3 runs) | 108 | **1** | 107 |

**The one completed flagship cell** (Approach A, live opencode, `typebox` rename of
`box`→`boxedType`, 13-file multi-file rename, contributor-free model):

- api_calls = 23 · input = 111,584 tok · output = 2,000 tok
- cache read = 191,105 · cache write = 0 · latency = 831 s (= 14 min)
- changed-file set R = 13 files, ground-truth G = 13 files, R∩G = 13
- **precision = 1.0, recall = 1.0, F1 = 1.0** (set-identical to the committed
  offline JDT ground truth; honesty-verified: 0 leftover old symbol, 13 of 13
  files carry the new name). Verdict: **PASS** for that cell.

The raw 123.8 KB JSON event stream for that cell is archived at
`cells/javapoet/typebox_A_r1.events.jsonl`; metrics extraction & scoring harness
under `harness/` ran on it offline.

## What this report is NOT

- It is **not** a claim that "the eval is done" — 107/108 cells are pending.
- It is **not** a claim of an average over the matrix (there is no matrix yet).
- It is **not** a claim that every metric extractor branch was exercised beyond
  this one stream.

## What finishing the matrix looks like (next session, repeatable)
`harness/run_cell.sh --cell <proj>/<task>_<arm>_r<i>` per remaining cell, then
`harness/score.py` vs committed offline dry-run truth. Rough budget from the data
point: ~14 min × 107 ≈ **~25 hours wall-clock** at the contributor-free model —
a dedicated-run commitment, not something I'll pretend finished in a session
that completed one flagship cell.
