# java-refactoring-mcp

A headless Java refactoring tool powered by Eclipse JDT. Exposes semantic
refactoring operations through a CLI and (soon) an MCP server so that coding
agents can safely rename, extract, and restructure Java code without touching
strings or regexes.

## Architecture

```
CLI ───────┐
           ├──> JdtRenamer (refactor-core) ──> Eclipse JDT ASTParser / ASTRewrite
MCP ───────┘         │
                 MavenProject
               (source roots, classpath)
```

| Module          | Role |
|-----------------|------|
| `refactor-core` | Refactoring engine, JDT integration, Maven project model |
| `refactor-cli`  | Thin CLI layer (Picocli) — no refactoring logic |
| `refactor-mcp`  | MCP server (stdio, `io.modelcontextprotocol.sdk`) — no refactoring logic |

## What Works

### M0 — Headless JDT proof of concept
Standalone JVM process parses Java, resolves semantic bindings via
`ASTParser`, and rewrites source via `ASTRewrite` — no Eclipse IDE, no SWT,
no display server.

### M1 — Test fixture framework
`Fixtures` (fixture loading, `offsetOf`, `lineCol`) and `RenameStoryBoard`
(fluent `MarkdownStoryBoard` builder) shared across all tests.
ApprovalTests golden-master storyboards show: input → refactoring → output.

### M2 — Maven project model
`MavenProject` reads source roots and Java version from `pom.xml`; resolves
the dependency classpath via `mvn dependency:build-classpath` (cached).
`JdtRenamer` uses the project classpath so external types resolve correctly.

### M3 — Rename (multi-file)
`JdtRenamer.rename(project, file, offset, newName)` uses
`ASTParser.createASTs()` to parse all project source files together, resolves
cross-file bindings, and rewrites every affected file.

Supported targets (located by file + line + column):

| Target | Scope |
|--------|-------|
| Local variable | Single file |
| Parameter | Single file |
| Field | All project files |
| Method | All project files |
| Type | All project files (class decl, constructor, usages) |

Precondition: targeting a constructor name directly is rejected with a
diagnostic ("rename the type instead").

### M4 — CLI
```
java-refactor rename \
  --file src/main/java/com/example/Calculator.java \
  --line 4 --column 16 \
  --name plus
```

| Option | Description |
|--------|-------------|
| `--file` / `-f` | Source file containing the symbol |
| `--line` / `-l` | 1-based line number |
| `--column` / `-c` | 1-based column number |
| `--name` / `-n` | New name |
| `--dry-run` | Print new source for each changed file; do not write to disk |
| `--project` | Maven project root (auto-detected by walking up from `--file`) |

#### Example — dry run

```
$ java-refactor rename -f src/main/java/com/example/Calculator.java \
    -l 4 -c 16 -n plus --dry-run

Dry run — no files written.
Changed files (2): App.java, Calculator.java

=== App.java ===
package com.example;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        int result = calc.plus(1, 2);
        System.out.println(result);
    }
}

=== Calculator.java ===
package com.example;

public class Calculator {
    public int plus(int a, int b) {
        return a + b;
    }
}
```

### M5 — MCP server

`java -jar refactor-mcp.jar` starts a stdio MCP server.

Three tools are exposed:

| Tool | Description |
|------|-------------|
| `list_refactorings` | Lists available refactoring operations |
| `analyze_refactoring` | Dry-run: returns new source of every changed file |
| `apply_refactoring` | Applies changes and writes files to disk |

Parameters for `analyze_refactoring` and `apply_refactoring`:

| Parameter | Type | Description |
|-----------|------|-------------|
| `project_root` | string | Absolute path to the Maven project root |
| `file` | string | Source file (absolute or relative to `project_root`) |
| `line` | integer | 1-based line number |
| `column` | integer | 1-based column number |
| `refactoring` | string | `"rename"` (only supported type currently) |
| `new_name` | string | New name for the symbol |

### M6 — Extract Method

`JdtExtractor.extractMethod(source, unitName, selectionStart, selectionLength, methodName)`

Extracts a range of statements into a new private method using JDT AST analysis (headless, no workspace needed).

| Case | Behaviour |
|------|-----------|
| No params, no return | `void extracted()` |
| Pre-declared vars read in selection | added as parameters |
| Single var declared in selection, used after | returned; call gets `Type var = extracted(...)` |
| Selection contains `return` | rejected with diagnostic |
| Multiple vars declared in selection, used after | rejected with diagnostic |

CLI: `java-refactor extract --file F --start-line L --start-column C --end-line L2 --end-column C2 --name methodName [--dry-run]`

MCP tool: `extract_method` — returns the rewritten source, does not write to disk.

### M7 — Inline Variable

`JdtInliner.inlineVariable(source, unitName, offset)` replaces every use of a local variable with its initializer expression and removes the declaration. Parenthesises compound expressions conservatively.

CLI: `java-refactor inline-var --file F --line L --column C [--dry-run]`

MCP tool: `inline_variable` — returns rewritten source, does not write to disk.

### M8 — Extract Variable

`JdtExtractVariable.extractVariable(source, unitName, selStart, selLen, varName, replaceAll)`

Introduces a local variable for the selected expression. With `replaceAll=true`, replaces every textually identical occurrence in the enclosing block.

CLI: `java-refactor extract-var --file F --start-line L --start-column C --end-line L2 --end-column C2 --name N [--replace-all] [--dry-run]`

MCP tool: `extract_variable` — returns rewritten source, does not write to disk.

### M9 — Inline Method

`JdtInlineMethod.inlineMethod(source, unitName, offset)` replaces a method call with the method's body, substituting formal parameters with actual arguments. Single-file only.

Supported: void methods (body statements replace the call); value-returning methods with a single `return` statement (expression replaces the call). Method declaration is preserved.

CLI: `java-refactor inline-method --file F --line L --column C [--dry-run]`

MCP tool: `inline_method` — returns rewritten source, does not write to disk.

### M10 — Extract Constant

`JdtExtractConstant.extractConstant(source, unitName, selStart, selLen, constName, replaceAll)`

Introduces a `private static final` field at the top of the enclosing class for the selected expression. With `replaceAll=true`, replaces every textually identical occurrence in the class body.

CLI: `java-refactor extract-const --file F --start-line L --start-column C --end-line L2 --end-column C2 --name NAME [--replace-all] [--dry-run]`

MCP tool: `extract_constant` — returns rewritten source, does not write to disk.

### M11 — Introduce Parameter

`JdtIntroduceParam.introduceParam(project, sourceFile, selStart, selLen, paramName, paramType?)`

Promotes a selected expression to a new method parameter. Uses the same multi-file `ASTParser.createASTs` engine as rename to find and update all call sites in the project.

CLI: `java-refactor introduce-param --file F --start-line L --start-column C --end-line L2 --end-column C2 --name N [--type T] [--project P] [--dry-run]`

MCP tool: `introduce_param` — returns new source for every changed file.

### M12 — Remove Parameter

`JdtRemoveParam.removeParam(project, sourceFile, offset)` removes an unused formal parameter from a method and the corresponding argument from every call site in the project. Rejects if the parameter is referenced in the method body.

CLI: `java-refactor remove-param --file F --line L --column C [--project P] [--dry-run]`

MCP tool: `remove_param` — returns changed files.

## Planned

| Milestone | Description |
|-----------|-------------|
| M13+ | move, pull up/push down, extract interface/superclass, … |

## Design Principles

- **Semantic, not textual** — symbol resolution via JDT bindings; no string
  replacement, no regexes.
- **JDT-first** — delegates to Eclipse JDT for AST parsing, binding resolution,
  and source rewriting.
- **Headless** — runs as a normal JVM process; no IDE, no SWT, no display.
- **Thin CLI / MCP** — all refactoring logic lives in `refactor-core`; the CLI
  and MCP layers only parse arguments and format output.
- **Golden-master tested** — every refactoring has ApprovalTests storyboard
  fixtures showing input → named refactoring → output. Approval files are
  never regenerated blindly.
- **Safe failure** — unresolvable bindings and unsupported targets return a
  structured diagnostic instead of a best-effort modification.

## Building

```bash
mvn package          # compile + test
mvn test             # test only
```

Requires Java 21 and Maven 3.8+.
The rename engine shells out to `mvn dependency:build-classpath` once per
project to resolve dependencies; the result is cached in memory.

## Testing

Tests use [ApprovalTests](https://approvaltests.com/) with `MarkdownStoryBoard`.
Each approved `.md` file documents exactly what was refactored and what changed:

```markdown
# Rename method: add → plus (multi-file)

### Input: Calculator.java:
```java
public class Calculator {
    public int add(int a, int b) { return a + b; }
}
```

### Refactoring:
**rename method** `Calculator.add` → `Calculator.plus`

### Output: Calculator.java:
```java
public class Calculator {
    public int plus(int a, int b) { return a + b; }
}
```
```

To update an approved file after an intentional change:
1. Inspect the diff between the received and approved file.
2. Verify the new output is actually correct.
3. Copy `*.received.md` → `*.approved.md`.
