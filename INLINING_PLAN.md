# Aggressive Inlining — Next Steps Plan

Baseline branch: `feature/aggressive-inlining`
Baseline artefact: `INLINING.md` (investigation and spike results)

This plan continues from the isolated spike in
`refactor-core/src/main/java/com/github/pfichtner/refactoring/inlining/`.
The spike is not yet wired into CLI or MCP.

---

## Goal

Turn the isolated inlining spike into a project-wide, conservative aggressive
inlining engine without changing the semantics of existing refactorings.

The working rule stays:

> Prove locally, reject explicitly. If a safety condition cannot be shown from the
> JDT model, do not edit.

---

## Guardrails

- Keep the existing `inline_method`, `inline_variable`, and constant APIs unchanged.
- Add the new capability as a separate operation, exposed through CLI/MCP only after
  the engine API is stable.
- All source edits go through JDT AST nodes and `ASTRewrite`; no regex or raw string
  source surgery.
- Every successful transformation needs an approval fixture. Every rejection or bug
  becomes a regression fixture.
- Inspect approval diffs before accepting them. Never bulk-regenerate approvals.
- Run the full reactor before finishing each phase.
- Commit after each completed phase.

---

## Phase 1 — Harden the spike

Status: complete. `InliningEngineTest` has 31 tests; full reactor is green on JDK 21.

### Work

1. Add regression fixtures for:
   - `return` inside `try` and `finally`, including a `finally` that returns.
   - Existing labels in the caller and callee; generated labels must not collide.
   - Locals captured by an inner class, anonymous class, or lambda.
   - Pattern variables, enhanced-for variables, and catch parameters.
   - Overloaded methods and calls through `this`/`super`.
   - Argument expression with side effects used multiple times.
   - Direct recursion rejection and a mutual-recursion budget stop.
   - Assignment whose left-hand side is not a simple name with a side-effecting
     receiver or index.
2. Audit every supported call position. Add the missing rejection checks rather than
   widening accepted positions.
3. Ensure every successful approval test compiles the output with the JDK compiler.
4. Add focused tests for the two budgets:
   - `maxTransformations`
   - `maxCodeGrowthChars`
5. Review the generated formatting. Add a formatter only if mixed indentation makes
   approval output or downstream edits unreliable; otherwise document it.
6. Update `INLINING.md` with any behavior that changes.

### Acceptance

- All new fixtures compile where successful.
- Rejections carry a stable diagnostic fragment.
- `InliningEngineTest` passes.
- Full `mvn test` passes on JDK 21.
- No existing approval file changes unexpectedly.

### Commit

`feat(inlining): harden aggressive inlining safety checks`

---

## Phase 2 — Project-wide same-type parsing

Status: complete for the same-type and cross-type instance scope. Static cross-type
inlining and class-initialization analysis remain deferred.
`ProjectInliningEngineTest` has 11 tests; full reactor is green on JDK 21.

### Work

1. Replace the single-`CompilationUnit` entry point with a project-level engine driven
   by `JavaProject` and `JdtProjectSources.parseAll`.
2. Locate candidate calls across all parsed units using bindings, not source text.
3. Introduce an import step for cross-package types copied into a caller. Same-type
   calls already have the callee's imports in scope; cross-type calls use
   `InlineImports` to add AST import nodes and reject simple-name conflicts.
4. Keep the first project-wide scope conservative:
   - same type across files;
   - private/final/static methods only;
   - no constructor or structural inlining.
5. Apply a batch of non-overlapping transformations per pass, then re-parse once.
   The file is the non-overlapping batch unit: one candidate per file per pass.
6. Keep a deterministic candidate order so approvals stay stable.
7. Return a per-file change map plus diagnostics.

### Acceptance

- Multi-file fixtures prove the same-type restriction.
- Applying the engine does not modify files during analyze/dry-run.
- Batched passes produce the same result as one-at-a-time on the fixtures.
- Full reactor passes.

### Commit

`feat(inlining): add project-wide same-type inlining`

### Phase 2b — Cross-type instance inlining

Status: complete. Commit: `feat(inlining): support cross-type instance inlining`.

- Accept inherited final/private instance methods declared on a caller supertype.
- Reject cross-type static calls pending a class-initialization check.
- Reject generic declaring types, bodies using `super` or qualified `this`, and
  inaccessible types/members.
- Add normal and static imports with `InlineImports`; reject simple-name conflicts.
- Fixtures: successful cross-type instance and static-import runs; rejected static
  call, `super`, private member, and import conflict.

### Deferred

Cross-type static inlining and the class-initialization check move together to a later
step.

---

## Phase 3 — Data-flow and expression positions

Status: Phase 3b complete. Initial Phase 3c step complete (side-effect-free assignment LHS).

### Phase 3a — Pure argument substitution (complete)

1. Purity and binding-based use-count analysis for arguments.
2. Direct substitution only for literals, unqualified `this`, and caller local/parameter
   reads used once outside loops and closures. Written parameters and all other
   arguments keep the capture-once temporary.
3. Runtime-equivalence tests for substitution and for written, multi-use, loop-contained,
   and lambda-captured fallbacks.

### Phase 3b — Receiver capture (complete)

1. Capture same-type explicit receivers once before the arguments.
2. Add a null check and rewrite unqualified `this`, instance fields, and instance
   method calls to the captured receiver.
3. Keep cross-type, generic, and non-static nested explicit receivers rejected, along
   with `super`, qualified `this`, and local/anonymous classes in the body.
4. Added side-effect-once, null-receiver, private-member, cross-type, generic, and
   non-static nested fixtures.

### Phase 3c — Additional call positions (complete for key positions)

1. Assignment RHS with a side-effect-free LHS. (done: simple name and unqualified `this.field`)
2. Nested expressions with pure preceding sub-expressions and a safe insertion point. (done)
3. Return expressions already handled by the spike.
4. Definite-assignment and exception-flow checks for the new positions.

### Acceptance

- Evaluation-order tests compare original and transformed runtime results.
- Pure argument substitution is proven not to duplicate evaluation.
- Unsafe positions still reject with a diagnostic.
- Full reactor passes.

---

## Phase 4 — Call graph and cleanup

Status: complete.

### Work completed

1. `CallGraphAnalyzer` built per pass (single-file: from `CompilationUnit`;
   project-wide: from snapshot declarations and calls).
2. Mutual recursion (caller transitively reachable from callee in the call graph)
   rejected with "mutual recursion detected" before the semantic safety check.
3. Dispatch analysis deferred: virtual non-private/non-final/non-static methods
   remain rejected by the existing `SemanticSafetyAnalyzer` check.
4. Opt-in unused-declaration removal via `InlineConfig.removeUnusedDeclarations()`:
   after the fixed point, private methods with no remaining call sites are removed
   via `ASTRewrite`; results reported in `InliningResult.removed()` and
   `ProjectInliningResult.removed()`.
5. Removal uses a fresh parse of the final source state.
6. Only private methods are removed (guaranteed no external callers for single-file
   and project-wide runs).

### Acceptance

- Mutual recursion is rejected by analysis, not merely by budget exhaustion. ✓
- `start` in the Mutual fixture is correctly unrolled (not cross-inlined). ✓
- Removal of `helper()`, `twice()`, `plusTen()` is approval-covered. ✓
- Both single-file and project outputs compile and run correctly. ✓
- Full reactor passes (277 core + 84 CLI + 89 MCP + 13 demo = 463 tests). ✓

### Commit

`feat(inlining): add call graph analysis and opt-in unused-declaration removal`

---

## Phase 5 — Expression and structural inlining

Implement one item at a time, each with its own safety analyzer and fixtures:

1. Getter inlining for side-effect-free, non-overridable getters. **Status: complete.**
   `GetterInliningTransformation` substitutes the return expression of a single-return
   pure-body method directly at the call site (no hoisting) when the hoisting
   transformation rejects the position. Enabled positions: right operand of an impure
   infix, argument after an impure earlier arg, assignment RHS with side-effecting LHS.
   Three new fixtures (`getter-impure-body-nested`, `getter-nested-argument`,
   `getter-assignment-rhs`) and four new/updated tests in `InliningEngineTest`.
2. Compile-time constant inlining. **Status: complete.**
   `ConstantInliningTransformation` replaces references to `static final` fields with a
   non-null `IVariableBinding.getConstantValue()` with the corresponding literal AST
   node (`boolean`, `char`, `String`, integral and floating-point types; skips NaN and
   Infinity). Negative values use `PrefixExpression(MINUS, NumberLiteral)`. The engine's
   `inlineAll` and `inlineProject` loops try constant candidates after method candidates
   find no progress, so a constant reference inlined in one pass may expose a new method
   candidate in the next. Four approval tests: `constant-int`, `constant-string`,
   `constant-boolean`, and `constant-after-method-inline`.
3. Final-field inlining (non-static `final` instance fields). **Status: complete.**
   `FinalFieldInliningTransformation` replaces references to `private final` non-static
   fields whose declaration initializer is `isPure` (literals and constant arithmetic)
   with that initializer. Atomic initializers substitute directly; compound initializers
   are wrapped in `ParenthesizedExpression`. Both unqualified and `this.field` access
   are handled. The engine runs the final-field loop after the constant loop.
4. Constructor inlining for simple constructors only.
5. Delegation elimination as a composed transform.
6. Lambda and anonymous-class flattening.
7. Superclass/interface/wrapper flattening only after a separate design review.

### Acceptance per item

- Dedicated fixtures cover initialization order, `this`, capture, exceptions, and
  identity where relevant.
- Unsafe cases are rejected and recorded.
- Full reactor passes.

---

## Phase 6 — CLI and MCP exposure

### Work

1. Add an opt-in operation, for example `inline_method_aggressive`.
2. Keep both adapters thin; both call the same engine entry point.
3. Expose:
   - changed-file map;
   - applied count;
   - fixed-point status;
   - rejection diagnostics;
   - dry-run behavior.
4. Add CLI storyboard and MCP server tests.
5. Update `README.md` and `APPROVALS.md` when the operation becomes user-facing.

### Acceptance

- CLI and MCP produce identical results for the same fixture project.
- Analyze does not modify files.
- Full reactor passes.

### Commit

`feat(cli,mcp): expose aggressive inlining`

---

## Test commands

```bash
export JAVA_HOME=/tmp/jdk21
export PATH=/tmp/jdk21/bin:$PATH

# Focused
mvn -pl refactor-core -Dtest=InliningEngineTest,ProjectInliningEngineTest test

# Full reactor
mvn test
```

If JDK 21 is not present at `/tmp/jdk21`, install or point `JAVA_HOME` at a JDK 21
before running the commands.

---

## Open decisions

Resolve these before the phase that needs them:

1. Cross-class static inlining: how to preserve class initialization triggers.
2. Cross-type and generic explicit receiver support: capturing the receiver's
   enclosing instance and substituting type variables.
3. Method removal: opt-in flag versus always keeping declarations.
4. Formatting: decided to keep JDT output and document mixed/compact formatting; no
   formatter in Phase 1.
5. Public operation name and whether an existing tool receives an `aggressive` mode.
6. Batch strategy: resolved for project runs as one candidate per file per pass, then
   re-parse once. Multiple candidates in one file still wait for later passes.
7. Whether a final compile check requires a user-supplied classpath and how failure
   is reported.

---

## Definition of done for the next continuation

- Phases 1–4, Phase 5a (getter inlining), Phase 5b (constant inlining), and Phase 5c
  (private final instance-field inlining) are complete.
- Existing public APIs are unchanged.
- New operations are opt-in.
- Success paths compile and approvals were reviewed.
- Rejections are explicit and tested.
- Full reactor passes on JDK 21 (467 tests).
- `INLINING.md` and `INLINING_PLAN.md` reflect the delivered state.
- Next phase: Phase 5d (constructor inlining for simple constructors).
