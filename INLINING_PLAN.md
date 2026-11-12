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

Status: complete, including cross-type static inlining with class-initialization
analysis. `SemanticSafetyAnalyzer.classInitializationRejection` inspects the
declaring `TypeDeclaration` for static initializer blocks and non-constant static
field initializers. If none are found the call falls through to accessibility checks;
otherwise it is rejected with a targeted diagnostic. Enum and annotation declaring
types remain conservatively rejected.
`ProjectInliningEngineTest` has 13 tests; full reactor is green on JDK 21.

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

### Phase 2c — Cross-type static inlining with class-initialization analysis (complete)

- `SemanticSafetyAnalyzer.classInitializationRejection` replaces the blanket
  "not supported by spike" rejection for static cross-type calls.
- Declaring type must be a plain `TypeDeclaration` (not enum/annotation).
- Declaring type must have no `static {}` initializer blocks and no `static` field
  fragments with non-constant initializers (`IVariableBinding.getConstantValue() == null`).
- If safe: falls through to `inaccessibleSignature` + `inaccessibleBody` checks.
- Fixtures: `cross-type` (simple Beta, now succeeds) and `cross-type-static-init`
  (Beta with static block, still rejected with targeted diagnostic).
- Approval tests: `cross_type_static_method_is_inlined` (success) and
  `cross_type_static_call_is_rejected` (rejection, updated to use new fixture).

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
4. Constructor inlining for simple constructors only. **Status: complete.**
   `ConstructorInliningTransformation` inlines `this(args)` delegating constructor calls
   by replacing them with the target constructor's body. Parameters are captured in
   temporaries when not pure-single-use; pure single-use arguments are substituted
   directly. Early `return;` → labeled `break` (same mechanism as void method inlining).
   Rejected: explicit `super()` in target, chained `this()`, checked exceptions, generics,
   varargs. `InliningEngine.inlineAt` dispatches on `ConstructorInvocation`; `inlineAll`
   and `inlineProject` add a constructor-inlining tier between method and constant tiers.
   Six approval tests: three successes and two rejections.
5. Delegation elimination as a composed transform. **Status: complete.**
   `DelegationEliminationTransformation` replaces calls to methods whose body is a single
   delegated call (`return inner(args)` or `inner(args);`) with the inner call expression,
   substituting outer parameters with the outer call's actual arguments. The substitution
   is in-place: the outer call expression is replaced without hoisting, so it works at any
   call-site position including nested expressions where hoisting and getter inlining both
   fail. Inner args must be simple param refs, pure literals, or accessible field refs;
   non-pure outer args must correspond to inner args used at most once. Inner method must
   be in the same type with no explicit non-this receiver. `InliningEngine.tryTransform`
   tries delegation as the third fallback after hoisting and getter inlining. Two approval
   tests: `inlineAt` (single delegation step at nested position) and `inlineAll` (full
   two-pass chain: delegation then getter inlining).
6. Lambda and anonymous-class flattening. **Status: complete.**
   `AnonymousClassFlatteningTransformation` converts anonymous class declarations that
   implement a functional interface into lambda expressions. Safety conditions: exactly
   one body declaration (the SAM override), no field declarations, no unqualified `this`
   or `super` in the body, no nested type declarations, no generic/varargs SAM, no
   constructor arguments. Parameters use inferred types (`VariableDeclarationFragment`).
   Body is simplified to expression form for single-return or single-statement-expression
   bodies; otherwise a block lambda is produced. Added as the last tier in `inlineAll`
   and `inlineProject`, after final-field inlining. Rejections tracked in
   `InliningResult` / `ProjectInliningResult`. Four approval tests: `anon-runnable`,
   `anon-comparator`, `anon-with-this` (rejected), `anon-with-fields` (rejected).
7. Superclass/interface/wrapper flattening only after a separate design review.

### Acceptance per item

- Dedicated fixtures cover initialization order, `this`, capture, exceptions, and
  identity where relevant.
- Unsafe cases are rejected and recorded.
- Full reactor passes.

---

## Phase 6 — CLI and MCP exposure

Status: **complete**.

### Work completed

1. Added `inline-aggressive` CLI command (`InlineAggressiveCommand`) extending
   `AbstractRefactoringCommand`. Options: `--project`, `--max-transformations` (default 100),
   `--max-code-growth` (default 100 000), `--remove-unused-declarations`. Output reports
   transformation count and fixed-point status in the success line.
2. Added `inline_aggressive` MCP tool (`InlineAggressiveTool`). Required: `project_root`.
   Optional: `dry_run`, `max_transformations`, `max_code_growth_chars`, `remove_declaration`.
   Response includes applied count and fixed-point status prepended to the file listing.
3. Added two new `Property` enum values: `MAX_TRANSFORMATIONS`, `MAX_CODE_GROWTH_CHARS`.
4. Registered command in `Main.subcommands`; registered tool in `RefactoringServer.TOOLS`.
5. CLI storyboard tests: `CliRefactoringStoryBook.inline_aggressive` (dry-run) and
   `CliApplyStoryBook.inline_aggressive` (apply). Fixtures: `inline-aggressive-project/same-type`.
6. MCP tests: `RefactoringServerToolTest.tool_inline_aggressive_returns_preview` and
   `tool_inline_aggressive_applies`. Golden-master `RefactoringServerTest.list_refactorings_golden_master`
   updated to reflect 32 tools.

### Acceptance

- CLI and MCP produce identical inlined sources for the same fixture project. ✓
- Dry-run does not modify files. ✓
- Full reactor passes (298 core + 86 CLI + 91 MCP = 475 tests). ✓

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

## Phase 7b — Cross-type explicit receiver inlining

Status: **complete**.

---

## Phase 7c — Generic explicit receiver inlining

Status: **complete**.

`SemanticSafetyAnalyzer` gains `typeVariableUsageRejection(MethodDeclaration, ITypeBinding)`:
visits all `SimpleName` nodes in the method declaration (return type, parameter types,
body) and returns a rejection reason when any node resolves to a type variable binding
whose name matches one of the declaring type's formal type parameters.

**Same-type generic:** the blanket generic rejection is removed from
`explicitReceiverRejection`. Type variables declared by the class are always in scope
within the same-class caller, so no substitution is needed.

**Cross-type generic:** `crossTypeExplicitReceiverRejection` replaces the blanket
generic rejection with `typeVariableUsageRejection`. If the check passes (no type
variable reference found), the call falls through to the existing accessibility and
body-safety checks. If the check fails, the call is rejected with a targeted diagnostic
identifying the type variable name.

Three new tests in `InliningEngineTest`:
- `same_type_generic_explicit_receiver_is_inlined` (success, same-type `Foo<T>`)
- `cross_type_generic_explicit_receiver_without_type_var_is_inlined` (success, `Box<T>.size()` returns `int`)
- `cross_type_generic_explicit_receiver_with_type_var_is_rejected` (rejection, `Box<T>.get()` returns `T`)

Full reactor: 307 core + 86 CLI + 91 MCP tests pass.

### Commit

`feat(inlining): generic explicit receiver inlining (Phase 7c)`

`SemanticSafetyAnalyzer` gains `crossTypeExplicitReceiverRejection`: when a call
`other.method()` has an explicit receiver and the declaring type is not the caller's
type, the new path runs instead of `crossTypeRejection`. The subtype requirement is
dropped; the remaining checks are non-generic declaring type, non-static-nested
declaring type, no `super`/qualified-`this` in the body, no local/anonymous class
declarations in the body, accessible signature, and accessible body. The virtual
dispatch check still runs (the method must be `private`, `final`, or on a `final`
type). `explicitReceiverRejection` now handles same-type calls only (the `sameType`
parameter was removed). Two new single-file tests: `cross_type_explicit_receiver_is_inlined`
(success, using the existing `unsafe-explicit-receiver-cross-type` fixture) and
`cross_type_explicit_receiver_with_private_body_is_rejected` (rejection when body
references a private field). One new project-level test:
`cross_type_explicit_receiver_is_inlined` using a new `cross-type-explicit-receiver`
fixture with `Base.value()` returning `42` and `App.run()` calling `other.value()`.
Full reactor: 305 core + 86 CLI + 91 MCP tests pass.

### Commit

`feat(inlining): cross-type explicit receiver inlining (Phase 7b)`

---

## Phase 8 — Type-Variable Substitution for Cross-Type Generic Explicit Receiver Inlining

Status: **complete**.

`SemanticSafetyAnalyzer` gains `typeVarSubstitutionRejection` (replaces
`typeVariableUsageRejection` as the gate in `crossTypeExplicitReceiverRejection`):
- Rejects raw generic types (empty type arguments).
- Rejects wildcard and captured type arguments.
- Rejects cases where the body uses a type variable AND any type argument is itself
  parameterized or an array (body substitution is in-place SimpleName rename, which
  only works for simple types).
- Otherwise allows substitution.

`InlineMethodTransformation` gains three helpers:
- `buildTypeVarSubstitution(ITypeBinding)`: builds `Map<String, ITypeBinding>` from the
  call-site binding's declaring class type parameters and type arguments.
- `typeBindingToAstType(AST, ITypeBinding)`: converts an `ITypeBinding` to an AST `Type`
  node (handles primitives, arrays, parameterized types, simple types). Replaces the old
  `receiverType()` helper (which always used the erased qualified name).
- `substituteTypeVariables(ASTNode, ASTNode, Map<String,ITypeBinding>)`: mirror-walks
  original and copied body, renaming type-variable `SimpleName` nodes to their concrete
  counterparts via `setIdentifier(substituted.getName())`.

`InlineImports` gains an overload of `create` accepting `List<ITypeBinding> substitutedTypes`
and a private `collectTypeBindingImports` helper that recursively adds `Request` entries
for concrete types (skips primitives, type vars, wildcards, java.lang, same-package).

Receiver type, result type, and parameter-temp declarations now use `typeBindingToAstType`
instead of AST nodes copied from the declaration (which would carry the unsubstituted type
variable name).

New tests (single-file): `cross_type_generic_explicit_receiver_with_typed_return_is_inlined`
(Box<String>.get() where T get()), `cross_type_generic_explicit_receiver_with_typed_param_is_inlined`
(Box<String>.set(T value) where void set(T value)), and
`cross_type_generic_explicit_receiver_with_wildcard_is_rejected` (Box<?>).

Existing tests updated: `cross_type_generic_explicit_receiver_with_type_var_is_rejected`
renamed to `cross_type_generic_explicit_receiver_with_typed_return_is_inlined` (now a
success); approval files for `cross_type_generic_explicit_receiver_without_type_var_is_inlined`,
`same_type_generic_explicit_receiver_is_inlined`, and `ProjectInliningEngineTest.cross_type_explicit_receiver_is_inlined`
updated to reflect the cleaner receiver type (simple name from `typeBindingToAstType`
instead of erased qualified name from old `receiverType()`).

Full reactor: 310 core + 86 CLI + 91 MCP tests pass.

### Commit

`feat(inlining): type-variable substitution for cross-type generic calls (Phase 8)`

---

## Phase 9 — Complex-type-argument substitution

Status: **complete**.

`substituteTypeVariables` in `InlineMethodTransformation` now handles parameterized and
array type arguments via structural node replacement:
- When the substituted binding is `isParameterizedType()` or `isArray()`, the method
  finds the parent `SimpleType` of the matched `SimpleName` in the copied body and
  replaces it with the full type node produced by `typeBindingToAstType(ast, substituted)`.
- Simple type arguments (e.g., `String`) continue to use the existing `setIdentifier` path.
- `typeVarSubstitutionRejection` in `SemanticSafetyAnalyzer` now only rejects raw generic
  types and wildcard/captured type arguments; the former guard that rejected complex type
  arguments when the body used a type variable has been removed.
- Dead helpers `typeVariableUsageRejection` and `bodyUsesTypeVariable` have been removed.
- New test: `cross_type_generic_explicit_receiver_with_parameterized_type_arg_is_inlined`
  — `Box<List<String>>` with `final T copy() { T result = item; return result; }` inlined
  into `Foo.run`; the body type `T result` becomes `List<String> result`. Approval file
  reviewed and committed.
- Full reactor: 311 core + 86 CLI + 91 MCP tests pass.

### Commit

`feat(inlining): complex-type-argument substitution (Phase 9)`

---

## Definition of done for the next continuation

- Phases 1–4, Phase 5a–5f, Phase 6 (CLI/MCP exposure), Phase 7a–7c, Phase 8
  (type-variable substitution for cross-type generic explicit receiver inlining), and
  Phase 9 (complex-type-argument structural substitution) are complete.
- Existing public APIs are unchanged.
- New operations are opt-in.
- Success paths compile and approvals were reviewed.
- Rejections are explicit and tested.
- Full reactor passes on JDK 21.
- `INLINING.md` and `INLINING_PLAN.md` reflect the delivered state.
- Remaining limitations: the `crossTypeRejection` path (non-explicit-receiver
  supertype-inherited calls) still rejects all generic declaring types; type-variable
  substitution is only implemented for the explicit-receiver cross-type path.
- Next phase: extend generic substitution to the `crossTypeRejection` path (implicit
  receiver, supertype-inherited calls where the caller's superclass carries the type
  arguments), or superclass/interface/wrapper flattening (separate design review required).
