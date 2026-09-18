I want you to build a headless Java refactoring tool that exposes
Eclipse JDT's refactoring capabilities through both a CLI and an MCP
server.

The primary user is a coding agent. The tool should allow an agent to
perform safe, semantic Java refactorings on an existing Java project.

IMPORTANT:
Do not attempt to implement the whole project in one pass.

Work incrementally through milestones. Each milestone must leave the
repository in a working state with tests passing.

==================================================
PRODUCT VISION
==================================================

Build:

    java-refactor

which can be used as:

    java-refactor <command>

and:

    java-refactor mcp

The refactoring implementation should rely on Eclipse JDT's existing
semantic/refactoring infrastructure wherever possible.

Do NOT implement semantic Java refactoring using regexes or naive
text replacement.

The same underlying refactoring engine must be used by both the CLI
and MCP interfaces.

The initial target is Java projects, primarily Maven projects.
Gradle support can come later.

==================================================
CORE DESIGN PRINCIPLES
==================================================

1. Headless

The application must run as a normal Java process without requiring
the Eclipse IDE, SWT, a display server, or an Eclipse GUI.

Before building substantial infrastructure, verify that the required
JDT functionality can actually be used headlessly.

2. JDT-first

Prefer existing JDT APIs for:

	•	AST parsing
	•	bindings / semantic resolution
	•	refactoring
	•	precondition checking
	•	source rewriting

Do not reimplement a JDT refactoring unless there is a concrete reason
that JDT does not provide the required functionality.

3. Semantic, not textual

For example, renaming a symbol must resolve the actual Java symbol
and rename its references. It must not globally replace matching
strings.

4. CLI and MCP share the same engine

The architecture should look approximately like:

    CLI ───────┐
               ├──> Refactoring Engine ──> JDT
    MCP ───────┘

The CLI and MCP layers should contain little or no refactoring logic.

5. Test-driven development

Every refactoring capability must have automated tests.

Use golden-master / ApprovalTests-style tests heavily for source
transformations.

A typical fixture should look like:

    fixtures/
      rename/
        local-variable/
          input/
            Foo.java
          approved/
            Foo.java

The test runs the refactoring against input and compares the resulting
source/project against the approved golden master.

Do not merely assert individual AST properties when testing a source
transformation. The generated source is important.

6. Regression corpus

Every discovered bug or edge case should become a permanent fixture.

7. Safe failure

If a refactoring cannot safely be performed, return a structured
diagnostic rather than making a best-effort modification.

8. Do not silently change unrelated source.

9. Do not automatically commit to git.

==================================================
APPROVAL TESTING
==================================================

Approval/golden-master testing is a core part of this project.

Tests should cover both:

A. Successful transformations

    input source
       ↓
    refactoring
       ↓
    generated source
       ↓
    compare with approved source

B. Invalid/unsupported transformations

    input
       ↓
    refactoring analysis
       ↓
    structured diagnostics
       ↓
    compare with approved diagnostic output

For source transformations, prefer whole-file or whole-project
approval fixtures.

The coding agent MUST NOT blindly regenerate approval files to make
tests pass.

When an approval test changes:

1. inspect the diff
2. determine whether the new behavior is actually correct
3. only then update the golden master

==================================================
REFactoring MODEL
==================================================

Aim toward this conceptual API:

    analyze(request)
        -> RefactoringPlan

    apply(plan)
        -> RefactoringResult

A plan should contain enough information to understand what will
change before modifying files.

Conceptually:

    RefactoringPlan
      - refactoring type
      - affected files
      - diagnostics
      - proposed changes
      - diff / resulting source

The exact API can evolve based on what JDT provides.

Do not over-engineer this before the first real refactoring works.

==================================================
TARGET REFACTORINGS
==================================================

Ultimately expose as much of JDT's useful refactoring functionality
as is practical.

Likely capabilities include:

	•	rename local variable
	•	rename parameter
	•	rename field
	•	rename method
	•	rename type
	•	extract method
	•	extract variable
	•	extract constant
	•	inline method
	•	inline variable
	•	change method signature
	•	move
	•	pull up
	•	push down
	•	extract interface
	•	extract superclass
	•	introduce parameter
	•	and other JDT-supported refactorings

Do NOT implement all of these now.

First determine exactly which JDT refactoring APIs are available
headlessly in the selected JDT version.

==================================================
MILESTONES
==================================================

Implement the project incrementally.

M0 — Headless JDT proof of concept

Goal:

Prove that a standalone Java process can:

1. load a minimal Java project
2. parse Java source with JDT
3. resolve semantic bindings
4. invoke one JDT refactoring
5. obtain the resulting source
6. verify the result using a golden-master test

Use a very small rename example.

Do not build CLI/MCP yet.

This milestone exists primarily to eliminate architectural risk.

--------------------------------------------------

M1 — Test fixture framework

Build reusable infrastructure for:

	•	input project/source
	•	executing a refactoring
	•	collecting changed files
	•	comparing against approved files
	•	useful approval diffs
	•	diagnostic approvals

Create the first fixture corpus.

--------------------------------------------------

M2 — Headless project model

Create a minimal project abstraction.

Initially support Maven projects.

The project model should provide the information JDT needs, such as:

	•	source roots
	•	dependencies/classpath
	•	Java version/compiler configuration
	•	project files

Keep this minimal.

Do not attempt to support every Maven/Gradle feature yet.

--------------------------------------------------

M3 — Rename

Expose JDT rename functionality.

Initially support:

	•	local variables
	•	parameters
	•	fields
	•	methods
	•	types

Identify the target using source location rather than only a symbol
name.

For example:

    file + line + column

must resolve to the actual semantic Java element.

Create comprehensive approval fixtures covering:

	•	simple rename
	•	shadowing
	•	overloaded methods
	•	inheritance
	•	imports
	•	generics
	•	lambdas
	•	anonymous classes
	•	multiple files

Add negative/precondition tests where appropriate.

--------------------------------------------------

M4 — CLI

Expose the existing engine through a CLI.

Example:

    java-refactor rename \
      --file src/Foo.java \
      --line 10 \
      --column 12 \
      --name answer

Support preview/dry-run behavior.

The CLI must not contain its own refactoring logic.

--------------------------------------------------

M5 — MCP

Expose the same engine through MCP.

Initially provide a small generic interface, for example:

    list_refactorings
    describe_refactoring
    analyze_refactoring
    apply_refactoring

Do not duplicate the refactoring implementation in MCP tools.

--------------------------------------------------

M6 — Extract Method

Use JDT's extraction functionality if available.

Support source selections.

Test extensively:

	•	simple extraction
	•	parameters
	•	return values
	•	modified variables
	•	multiple inputs
	•	control flow
	•	exceptions
	•	loops
	•	conditionals
	•	lambdas
	•	nested scopes
	•	invalid selections

--------------------------------------------------

M7+ — Additional JDT refactorings

Add refactorings one at a time.

Each new refactoring must have:

	•	engine implementation
	•	CLI exposure where appropriate
	•	MCP exposure
	•	successful approval fixtures
	•	negative/precondition tests
	•	regression fixtures

Before adding each refactoring, inspect the JDT APIs and determine
the correct headless API rather than assuming one exists.

==================================================
CODING AGENT WORKFLOW
==================================================

You are working as an incremental coding agent.

At the beginning of each milestone:

1. inspect the existing repository
2. inspect the relevant JDT APIs/documentation available in the
   current dependencies
3. propose a short implementation plan
4. identify risks
5. implement ONLY that milestone

Do not jump ahead to future milestones.

After implementation:

1. run all relevant tests
2. run the full test suite
3. inspect approval diffs
4. fix implementation issues
5. verify that no unrelated files changed
6. summarize:
   - files changed
   - tests added
   - tests executed
   - important design decisions
   - known limitations
   - remaining work

Do not claim a milestone is complete if tests are failing.

==================================================
ARCHITECTURAL GUIDANCE
==================================================

Prefer a structure roughly along these lines, but adapt it if the
actual JDT APIs suggest a better structure:

    refactor-core/
    refactor-jdt/
    refactor-cli/
    refactor-mcp/
    refactor-test/

The exact module structure is not sacred.

Avoid speculative abstractions.

Only introduce abstractions when they are justified by the current
and immediately upcoming requirements.

=====================================
