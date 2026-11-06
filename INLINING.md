# Aggressive Java Inlining — Investigation and Technical Spike

Status: Phase 1 hardening and Phase 2 project-wide same-type and cross-type instance
inlining are complete. Cross-type static inlining, class-initialization analysis, and
CLI/MCP wiring remain deferred. The engine is an isolated prototype, not yet wired into
CLI/MCP. No existing public API was changed.

Central question:

> How far can we extend our existing Eclipse JDT-based refactoring tool to provide
> aggressive, iterative Java inlining while preserving Java execution semantics?

Short answer: far enough to make inlining a first-class engine capability, but not by
treating it as textual substitution. JDT is the right substrate. The safe limit is set
by semantic analysis, not by JDT. The spike proves the core mechanism (argument
capture, local copying, multiple returns, iterative fixed point, explicit rejection)
and also shows that every aggressive step beyond a private/final method in the same
type needs a named safety check or a whole-program analysis.

---

## 1. Existing architecture

### Modules

| Module | Responsibility |
|---|---|
| `refactor-core` | Headless refactoring engine, JDT bindings, source rewriting, project discovery |
| `refactor-cli` | Thin command-line adapter over the engine |
| `refactor-mcp` | Thin MCP adapter over the engine |
| `gilded-rose-demo` | Demo project |

The CLI and MCP layers do not contain refactoring logic. Any inlining engine belongs
in `refactor-core` and, when exposed, should be called through both adapters.

### Refactoring pattern

Existing operations follow the same shape:

1. Parse a `CompilationUnit` with `JdtProjectSources.parseUnitWithBindings` (snippet)
   or `parseAll` / `FileASTRequestor` (project).
2. Locate a node by offset (`NodeFinder`) or by a name locator.
3. Resolve an `IBinding` / `IMethodBinding` / `IVariableBinding`.
4. Validate preconditions and throw `IllegalArgumentException` with a diagnostic when
   the operation cannot be performed.
5. Produce source edits.
6. Return either a rewritten string (single file) or a `Map<Path, String>` /
   `List<FileChange>` (project).

### JDT setup

- `JdtProjectSources` centralises parsing:
  - `ASTParser.newParser(AST.JLS21)`
  - `setEnvironment(classpath, sourcePaths, null, true)`
  - `setResolveBindings(true)`
  - `setBindingsRecovery(true)`
- Project classpath and source roots come from `JavaProject`
  (`MavenProject` / `GradleProject` / `ExplicitProject`).
- Bindings are resolved through the DOM. `JdtRenamer` already uses `ASTRewrite` plus
  `org.eclipse.jface.text.Document` for source rewriting; `JdtExtractor` assembles
  output from AST-derived source positions. No parallel compiler infrastructure is
  needed.

### Source rewriting, imports, formatting

- `ASTRewrite` is the established safe rewriting facility (`JdtRenamer`,
  `JdtExtractor`). The spike uses it as well.
- Existing operations do not run a formatter. Copied nodes keep their original
  formatting; inserted nodes use JDT's default formatting (sometimes tabs).
- Imports are currently handled ad hoc by operations that introduce a type
  (`JdtMoveClass`, etc.). There is no shared `ImportRewrite` layer. Inlining uses
  `InlineImports`, an AST-node-based collector that adds the imports needed by a copied
  body or signature and rejects simple-name conflicts; it does not edit source text.

### Validation and tests

- Preconditions are enforced by validation methods and surfaced as diagnostics.
- Golden-master tests use `ApprovalTests` with `RefactoringStoryBoard` and fixtures
  under `src/test/resources/fixtures/...`.
- `ArchitectureTest` enforces package boundaries and forbids regex in `Jdt*` classes.
- Every refactoring has approved markdown files; the approved output is the spec.

### Reusable pieces identified

| Need | Existing piece | Reuse |
|---|---|---|
| Parse with bindings | `JdtProjectSources` | Yes, directly |
| Project classpath / roots | `JavaProject` | Yes |
| Binding-based call discovery | `JdtInlineMethod.findAllCallSites` | Pattern reused, improved |
| Source rewriting | `ASTRewrite` + `Document` | Yes |
| Validation diagnostics | Method-local checks + `IllegalArgumentException` | Yes |
| Approval test harness | `RefactoringStoryBoard`, `Approvals` | Yes |
| Indentation detection | `JdtProjectSources.detectIndent` | Not needed by the spike |

The spike deliberately does **not** reuse `JdtInlineMethod`'s text-substitution
approach. That implementation supports only a single return and performs parameter
substitution by source slicing. Aggressive inlining needs copied ASTs, scoped
renaming, argument temporaries, and return rewriting.

---

## 2. Inlining capability investigation

### 2.1 Method inlining

| Case | Existing `JdtInlineMethod` | Spike | Assessment |
|---|---|---|---|
| Private method, same type | Yes (single return or void) | Yes | Realistic; implementation proves it |
| Static method, same type | Partly | Yes (implicit/type-qualified) | Realistic |
| Instance method, same type, non-virtual | Partly | Yes if private/final or type is final | Must reject virtual dispatch otherwise |
| Cross-class method | No | Yes for guarded instance calls | Accepted only on a caller supertype, non-generic, no `super`/qualified `this`, all copied types/members accessible; imports are added. Static calls stay rejected pending class-initialization analysis |
| Multiple returns | No | Yes (result temp + labeled break) | Realistic for statement-root call sites |
| Local variables | No scoping/renaming | Yes (copy + collision rename) | Realistic |
| Checked exceptions | Not analysed | Rejected | Feasible with call-site catch/`throws` analysis |
| Generic method parameters | Not analysed | Rejected | Feasible; needs type substitution and erasure-aware copying |
| Delegation / forwarding | Not distinguished | Not distinguished | Feasible; same transformation plus a policy for removing the wrapper |
| Recursive calls | Direct recursion rejected | Direct recursion rejected | Indirect recursion needs a call graph; do not rely on a budget |
| Side-effecting arguments | Naive substitution | Arguments bound once to temporaries | Realistic and essential |
| Constructor | Not supported | Not supported | Different semantics (allocation/initialization); separate milestone |

### 2.2 Variable and expression inlining

| Case | Existing | Assessment |
|---|---|---|
| Local variable | `JdtInliner` replaces every use with the initializer and removes the declaration | Works for simple cases; must reject multiple evaluation of side-effecting initializers or bind once |
| Constant | `JdtInliner.inlineConstant` for `static final` | Realistic; use binding and initializer |
| Field | No | Feasible for `final` fields; mutable fields need data-flow and alias analysis |
| Temporary variable elimination | No | Feasible via the same local-variable inliner |
| Getter inlining | No | Feasible for side-effect-free, non-overridable getters; otherwise virtual dispatch and side effects apply |
| Parameter inlining | No | Feasible at a single call site with argument capture |
| Lambda expression inlining | No | Feasible when target type is known; lambda capture and `this` semantics need dedicated tests |

### 2.3 Structural inlining

These are not equivalent to "inline method" and need separate semantic models.

| Case | Feasibility | Main obstacle |
|---|---|---|
| Nested class to top level | Already implemented by `JdtConvertNestedToTopLevel` | Not "inlining"; visibility and qualification |
| Anonymous class inlining | Feasible for simple single-use classes | `this`, capture, initialization order, type identity, serialization |
| Constructor inlining | Feasible for simple constructors | Allocation, field initialization, `super`/`this` chaining, final fields, exceptions |
| Superclass inlining | Technically possible by flattening members | Access, overriding, dispatch, initialization order, binary compatibility; rarely safe |
| Interface implementation simplification | Policy/design transformation more than inlining | Default methods, dispatch, API compatibility |
| Delegation elimination | Feasible as a composed transform | Requires proving the delegate is not shared/aliased and call sites are complete |
| Wrapper class elimination | Research-grade in practice | Whole-program analysis, reflection, type identity, serialization, public API |

Recommendation: implement method/variable/parameter/constant inlining first, then
constructor and delegation elimination as separate engines. Do not start with
superclass or wrapper elimination.

---

## 3. Semantic correctness analysis

The spike's safety rule is: if a condition cannot be proven from the local JDT model,
reject the candidate with a diagnostic. It never edits first and checks later.

### 3.1 Evaluation order and side effects

Java evaluates the receiver, then arguments left to right, then enters the method.
The spike binds each argument to a synthetic local in the callee's parameter order
before copying the body. Therefore:

- every argument is evaluated exactly once;
- argument order is preserved;
- parameter uses observe the captured value even if the body mutates the parameter;
- an unused parameter still evaluates its argument, as Java requires.

`InlineMethodTransformation` only hoists when the call is the whole expression of a
supported statement. That keeps the call's evaluation position relative to earlier
statements. Nested expressions such as `1 + f()` are rejected because inserting the
body before the statement would move `f()` before `1`. Assignment is accepted only
when the left-hand side is a simple name; otherwise the LHS receiver/index evaluation
would be reordered.

### 3.2 Multiple evaluation

Naive substitution of `f(x)` into `return x + x` evaluates `x` twice. The spike never
does this: parameter uses are replaced with a temporary assigned once. Local
variable inlining (`JdtInliner`) still has the older multiple-evaluation risk; it should
adopt the same capture rule before being called aggressive.

### 3.3 Virtual dispatch and overloading

- Overloads are identified by `IMethodBinding.getMethodDeclaration().getKey()`.
- A non-private, non-final instance method on a non-final type can be overridden.
  Inlining the declaration's body would change dynamic dispatch. The spike rejects
  this case.
- Static methods are non-virtual. Calls through a value expression are rejected
  because Java evaluates that receiver even though the method ignores it.
- Cross-type instance calls are accepted only when the callee is declared on a caller
  supertype and remains non-virtual; cross-type static calls and bodies using `super`
  are rejected because the spike does not model class initialization or the caller's
  superclass binding.

### 3.4 Scope, capture, `this`, and `super`

- The copied body is renamed only where a local would collide with a name visible in
  the caller. Binding keys drive both declaration and use renames, so shadowing is
  preserved.
- Parameters become locals, so assignment to a parameter inside the body still
  affects only the captured copy.
- The spike accepts instance methods only when the receiver is implicit or `this`.
  In that case `this` and `super` in the body still denote the same object.
  An explicit receiver such as `other.read()` is rejected because `this` would change.
- Calls inside anonymous classes are rejected because the nearest named type check
  fails; calls inside lambdas retain the enclosing method's `this`.

### 3.5 Generics

Method-level type parameters are rejected because parameter substitution and return
type copying would need type-variable substitution. Class-level type variables are
safe while the declaration and call share the same type. Cross-class generic
inlining is a research item.

### 3.6 Exceptions and reachability

- A checked exception declared by the target is rejected. Inlining would move the
  obligation to catch/declare it to every call site.
- Unchecked exceptions propagate in the same order because argument capture and body
  statements preserve sequencing.
- Returns are rewritten to assignments plus a labeled `break`. A `finally` block
  still runs on the break, so `return`/`finally` ordering is preserved.
- Bodies that never complete normally are rejected when they have no return, because
  pasting a terminal `throw` into the caller can make following code unreachable.

### 3.7 Synchronization

A `synchronized` method is rejected: inlining removes the method-level monitor.
`synchronized` statements inside the body are copied unchanged, so their lock scope
is preserved. Cross-object locking requires alias analysis and is out of scope.

### 3.8 Returns, break, and continue

- Value returns become `result = expr; break label;`.
- Void returns become `break label;`.
- Returns inside lambdas or nested/anonymous methods are not rewritten.
- `break`/`continue` inside loops and switches in the body remain bound to those
  constructs.
- Generated labels are fresh against all identifiers in caller and callee. Callee
  labels that collide with caller labels are renamed together with their
  `break`/`continue` references.

### 3.9 Access and initialization order

- Same-type inlining preserves access and does not add a class-initialization edge.
- Cross-class static inlining can remove the class-initialization trigger that the
  original invocation performed. Static cross-type calls stay rejected until that check
  exists.
- Cross-type instance inlining is accepted for non-generic callee types on the caller's
  supertype chain. Bodies using `super` or qualified `this`, and members/types the
  caller cannot access, are rejected.

### 3.10 Decision summary

| Condition | Spike decision |
|---|---|
| Private/final/static, same type, whole-statement call | Accept |
| Multiple returns, locals, language constructs inside body | Accept |
| Value-returning, side-effecting arguments | Accept (capture once) |
| Nested expression call position | Reject |
| Explicit instance receiver | Reject |
| Virtual dispatch possible | Reject |
| Checked exception declared | Reject |
| Generic method, varargs | Reject |
| Synchronized method | Reject |
| Cross-type instance on caller supertype, final/non-virtual, accessible, no `super`/generic | Accept |
| Cross-type static, generic declaring type, body uses `super`/qualified `this`, inaccessible member/type | Reject |
| Direct recursion, anonymous receiver | Reject |
| Body cannot complete normally and has no return | Reject |

---

## 4. Proposed inlining architecture

### 4.1 Package shape

The spike is isolated in:

```
com.github.pfichtner.refactoring.inlining
├── InliningEngine                 orchestration, candidate loop, public entry points
├── InlineMethodTransformation     one AST-based method-call inline
├── SemanticSafetyAnalyzer         local semantic proof / rejection
├── InliningSupport                AST navigation shared by the above
├── InlineConfig                   budgets
├── InliningResult                 single-CU applied + rejected + fixed-point status
├── ProjectInliningResult          per-file changes, applied + rejected, fixed-point
└── InlineRejectedException        diagnostic for refused transformations
```

The package depends on `JdtProjectSources` in the root package. The root package does
not depend on it, so the spike can be removed or promoted without touching existing
APIs. No `Jdt*` class was added outside the root package, so `ArchitectureTest`
continues to hold.

### 4.2 Evolution beyond the spike

| Conceptual component | Proposed role | Spike status |
|---|---|---|
| `JavaInliningEngine` | Entry point, budget handling, candidate scheduling | `InliningEngine` (single CU) |
| `InlineMethod` | Method-call transformation | `InlineMethodTransformation` |
| `InlineVariable` | Local/temporary/constant/field capture | Existing `JdtInliner`; not unified |
| `InlineConstant` | Constant references | Existing `JdtInliner.inlineConstant` |
| `InlineField` | Final field extraction with alias checks | Not implemented |
| `InlineParameter` | Single-call-site argument capture | Partly covered by method inlining |
| `InlineConstructor` | Allocation + initialization flattening | Not implemented |
| `InlineLambda` | Lambda target/type substitution | Not implemented |
| `InlineAnonymousClass` | Single-use class flattening | Not implemented |
| `SemanticSafetyAnalyzer` | Central safety gate | Implemented for the spike subset |
| `CallGraphAnalyzer` | Recursion, dispatch, reachability | Not implemented; direct recursion only |
| `DataFlowAnalyzer` | Multiple evaluation, purity, definite assignment | Argument capture only |
| `IterativeInliningOptimizer` | Fixed-point/budgeted scheduler | `inlineAll` (single CU) and `inlineProject` (project, same-type) |

### 4.3 Iterative pipeline

1. Parse the current source with bindings.
2. Collect candidate call sites and resolve declarations.
3. Run the safety analyzer for each candidate.
4. Apply one safe transformation.
5. Re-parse (fresh bindings and source positions).
6. Repeat until no candidate applies or a budget is exhausted.

`inlineAll` deliberately applies one transformation per pass. That is correct and easy
to reason about, but O(passes × files). `inlineProject` applies at most one
non-overlapping transformation per file per pass, then re-parses all files once. Because
a file is the unit of rewriting, edits in different files cannot overlap; a second
candidate in the same file waits for the next pass. Correctness must be validated by a
final re-parse plus compile check when a classpath is available.

Project sources are snapshotted into a temporary tree for each parse pass, because
`JdtProjectSources.parseAll` reads from disk. The original files are never touched; the
temporary tree is mapped back to the original paths and deleted after parsing.

### 4.4 Budgets

Implemented now:

- `maxTransformations` (per single-CU run, project-wide for `inlineProject`)
- `maxCodeGrowthChars` (per single-CU run; total project growth relative to the
  original sources for `inlineProject`)

Proposed next:

- `maxInliningDepth`
- `maxRecursionDepth`
- `maxResultingMethodSize`
- per-file and per-project growth
- wall-clock budget / cancellation

Budgets are a backstop, not a safety proof. A semantic rejection is always preferred
over "we ran out of budget".

### 4.5 Integration

- Keep the engine headless and independent of CLI/MCP.
- Add a new operation (for example `inline_method_aggressive`) rather than changing
  the semantics of the existing `inline_method` tool.
- Both CLI and MCP should call the same engine method.
- Dry-run remains the adapter's job: the engine returns source and diagnostics.

---

## 5. Technical spike

### 5.1 Implemented transformations

- Simple private method call, static call through the type, and explicit `this`
  receiver resolved by binding.
- Method with local variables, multiple returns, `try/finally`, and existing labels.
- Locals captured by lambdas, anonymous classes, and local classes.
- Pattern variables, enhanced-for variables, and catch parameters.
- Side-effecting argument used multiple times (captured once).
- Simple-name assignment right-hand side.
- Iterative chain `first → second → third` until fixed point.
- Project-wide same-type inlining across files, with one candidate per file per pass
  and a fixed point after all call sites are gone.
- Project-wide cross-type instance inlining when the callee is inherited from a caller
  supertype; missing type and static imports are added to the caller.
- Import conflicts, inaccessible types/members, generic declaring types, and bodies
  using `super` or qualified `this` are rejected with diagnostics.
- Project-wide cross-type static calls are rejected with the class-initialization
  diagnostic.
- Budget stops for `maxTransformations` and `maxCodeGrowthChars`, including mutual
  recursion and project-wide growth.
- Explicit rejection of synchronized methods, explicit receivers, checked
  exceptions, nested-expression call positions, `super`/constructor calls, method
  references, side-effecting assignment left-hand sides, direct recursion, and
  non-completing bodies.

### 5.2 Algorithm

For a value-returning call at a supported statement root:

1. Bind each argument to a fresh local of the formal parameter's type.
2. Copy the callee body with `ASTNode.copySubtree`.
3. Rename callee locals only when their names collide with caller-visible names.
4. Rename callee labels that collide with caller labels, including their
   `break`/`continue` references.
5. Replace parameter uses with the captured locals, using binding keys from the
   original body.
6. Create a result local of the declared return type.
7. Rewrite each own `return expr` into `result = expr; break label;`.
8. Insert the argument/results statements before the containing statement and replace
   the call expression with the result local.

Void calls replace the expression statement with the copied body. Early returns use
the same labeled break so they exit the whole inlined body, not just an inner loop.

Project-wide (`inlineProject`):

1. Snapshot all current sources into a temporary tree mirroring the project source
   roots.
2. Parse all units once with `JdtProjectSources.parseAll`.
3. Build a declaration index keyed by `IMethodBinding.getMethodDeclaration().getKey()`.
4. Visit calls in deterministic order: files sorted by normalized path, calls in
   reverse AST order within a file.
5. Apply the first safe candidate per file, then re-parse once for the next pass.
6. Repeat until no candidate applies or a budget stops the loop.

Same-type candidates share a compilation unit, so the callee's imports are already in
scope. Cross-type candidates collect the type and static member references in the
copied signature and body, add the needed imports as AST nodes, and reject simple-name
conflicts instead of guessing.

### 5.3 Tests

`refactor-core/src/test/java/com/github/pfichtner/refactoring/inlining/InliningEngineTest.java`

- 26 approval/semantic tests.
- Successful outputs are compiled with the JDK compiler.
- Runtime tests compile and invoke original and transformed classes, covering
  side-effecting arguments, `try/finally` ordering, captures, labels, overloads, and
  static calls.
- The chain test asserts a fixed point after three transformations; budget tests assert
  the applied count and fixed-point flag.
- Rejections assert the diagnostic fragment and record it in approved markdown.

`refactor-core/src/test/java/com/github/pfichtner/refactoring/inlining/ProjectInliningEngineTest.java`

- 11 approval/semantic tests for project-wide runs.
- Same-type fixture: two files change, both outputs compile and run.
- Cross-type instance fixture: the subclass output gains the `Duration` import, compiles,
  and runs.
- Cross-type static-import fixture: the caller gains the static `Math.abs` import,
  compiles, and runs.
- Cross-type rejections: static call, `super` usage, private member, import conflict.
- Batch equivalence: project result for each file equals single-file `inlineAll`.
- Dry-run: fixture bytes are unchanged on disk.
- Budgets: `maxTransformations` and project-wide `maxCodeGrowthChars`.

Fixtures live under
`refactor-core/src/test/resources/fixtures/inline-aggressive/` and
`refactor-core/src/test/resources/fixtures/projects/inline-aggressive-project/`.

The full Maven test suite passes: 265 core + 84 CLI + 89 MCP + 13 demo tests (451 total).

### 5.4 What the spike proves

- JDT bindings + `ASTRewrite` are sufficient for non-trivial method inlining.
- Multiple returns and argument side effects can be handled without text replacement.
- A conservative safety gate can reject the cases the implementation does not model.
- Iterative inlining can reach a fixed point by re-parsing after each application.
- Project-wide same-type and guarded cross-type instance inlining can run
  deterministically without touching project files during analysis.
- Imports for copied bodies and signatures can be added through AST nodes, with
  conflicts rejected rather than resolved by guessing.

---

## 6. Alternative technologies

| Technology | Headless | Java semantics | Method inline | Variable inline | Structural transforms | Integration / maintenance |
|---|---|---|---|---|---|---|
| Eclipse JDT Core (current) | Yes | Compiler-grade DOM + bindings | No ready-made headless inline API; DOM/`ASTRewrite` are sufficient | Same | Same | Already integrated, EPL-2.0, low new cost |
| JDT UI inline refactoring | No (IDE/LTK stack) | Yes | Yes, IDE-oriented | Yes | Some | High: pulls UI/runtime dependencies; violates headless constraint |
| IntelliJ Platform / PSI | Technically possible with a large platform setup | Excellent | Yes, IDE refactoring engine | Yes | Yes | Heavy; platform lifecycle, indexes, Apache-2.0 but operationally costly |
| OpenRewrite | Yes | Type-attributed LST, not full JDT binding model | Recipes exist for some transforms, not a general safe inline engine | Limited | Recipe-based | Apache-2.0 core; good for batch recipes, less suitable as a semantic proof engine |
| Spoon | Yes | Mature Java metamodel, source positions | Possible to implement; not a ready safe inline engine | Possible | Strong structural API | MIT / CeCILL-C; parallel model, would duplicate existing JDT investment |

Recommendation: do not replace JDT Core. The project already depends on compiler-grade
bindings and `ASTRewrite`. JDT UI's inline refactoring is not headless. OpenRewrite and
Spoon are credible for recipe-style migrations, but adopting either would create a
second semantic model and a second source-rewriting stack. If a complement is desired
later, OpenRewrite could serve as an external verification/recipe layer, not as the
engine.

---

## 7. Limitations and known edge cases

Implemented but intentionally narrow:

- Single-CU runs handle one named type. Project runs add guarded cross-type instance
  calls, but only when the callee is inherited from a caller supertype; arbitrary
  cross-type callers/callees are still rejected.
- Calls must be the whole expression of a variable initializer, return, simple
  assignment, or expression statement.
- No direct expression substitution; all value calls are hoisted.
- No declaration removal or unused-method cleanup.
- Cross-type static inlining and the class-initialization check are deferred.
  Cross-type imports are otherwise handled, with simple-name conflicts rejected.
- No call graph beyond direct recursion; mutual recursion is stopped only by budget.
- No data-flow purity analysis; every argument gets a temporary.
- Method-level generics and varargs are rejected.
- `synchronized` methods and checked exceptions are rejected.
- Formatting is not normalised; inserted code can mix tabs and spaces, and modified
  copied bodies may fall back to compact JDT formatting. The approved output is the
  spec, so downstream edits stay AST-based.
- The engine does not perform a final project compile; tests compile single outputs
  and the changed project sources with the JDK compiler.

Known edge cases to test before promotion:

- Bridge methods.
- Static field imports and on-demand imports.
- Method invocation inside an argument of the same method.
- Initialization order for cross-class static calls.
- Very large method bodies.

---

## 8. Recommendation and roadmap

### Classifications

**Implemented and tested**

- Same-type private/final/static method inlining.
- Local variable copying and collision renaming.
- Multiple value returns via result temporary and labeled break.
- Argument capture with exactly-once evaluation.
- Iterative fixed-point inlining of a call chain.
- Project-wide same-type inlining across files, one candidate per file per pass.
- Project-wide cross-type instance inlining from a caller supertype, with normal and
  static import insertion and conflict rejection.
- Semantic rejection with diagnostics for the unsafe subset.

**Technically feasible, not implemented**

- Cross-type static method inlining with class initialization analysis.
- Explicit receiver inlining by binding the receiver to a temporary and substituting
  `this` carefully.
- Getter, constant, final-field, parameter, and temporary inlining.
- Direct expression substitution when arguments are pure and used at most once.
- Call-graph-based recursion detection and removal of unused declarations.
- Formatter integration and compile verification.

**Requires substantial research**

- Constructor/superclass/interface/wrapper inlining.
- Anonymous class and lambda flattening with capture and identity preservation.
- Whole-program devirtualization and initialization-order proofs.
- Reflection, serialization, and framework-observable type identity.

**Cannot safely be implemented under the current conservative assumptions**

- Inlining arbitrary virtual methods without whole-program dispatch analysis.
- Inlining `synchronized` methods while preserving the method monitor.
- Inlining checked-exception methods without call-site exception analysis.
- Flattening superclasses/wrappers while preserving binary/API compatibility.

### Roadmap

1. **Harden the spike** (complete): finish statement positions (including assignment
   LHS side-effect checks), add `try/finally`, capture, label, and overload fixtures,
   add final compile verification for every approved output.
2. **Project model** (partial): project-wide same-type and guarded cross-type instance
   inlining with imports are complete. Next: exact class-initialization analysis for
   cross-type static calls.
3. **Data flow**: purity and multiple-evaluation analysis; argument capture only when
   needed; receiver capture; enable direct expression substitution safely.
4. **Call graph**: recursion (direct and mutual), dispatch, reachability, unused
   declaration removal.
5. **Expression inlining**: getters, constants, final fields, parameters, temporaries.
6. **Structural phase**: constructor and delegation elimination first; anonymous class
   and lambda next; superclass/wrapper flattening only with a separate design review.
7. **Adapters**: expose an opt-in aggressive operation through CLI and MCP once the
   engine API and diagnostics are stable.

The next implementation phase is the cross-type remainder of item 2: import rewriting,
cross-package inlining, and the class initialization check. Same-type project-wide
inlining already works without weakening the semantic gate.
