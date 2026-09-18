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

### M9 — Inline Method (multi-file)

`JdtInlineMethod.inlineMethod(project, sourceFile, offset, removeDeclaration)` inlines at **all call sites** in the project. Optionally removes the method declaration. Parameter substitution uses binding keys, so it works correctly across file boundaries.

Single-file snippet API (`inlineMethod(String, String, int)`) preserved for backward compatibility.

Supported: void methods (body statements replace each call); value-returning methods with a single `return` statement (expression replaces the call).

CLI: `java-refactor inline-method --file F --line L --column C [--project P] [--remove-declaration] [--dry-run]`

MCP tool: `inline_method` — accepts `project_root` and `remove_declaration`; returns new source for every changed file.

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

### M13 — Extract Interface

`JdtExtractInterface.extractInterface(classSource, unitName, interfaceName, methodNames?)`

Generates a new Java interface from the public non-static methods of a class and adds `implements InterfaceName` to the class declaration. Preserves parameter types, throws clauses, and generic type parameters. Returns `Result(modifiedClassSource, interfaceSource)` — the caller decides where to write the new file.

CLI: `java-refactor extract-interface --file F --name N --interface-file PATH [--methods m1,m2] [--dry-run]`

MCP tool: `extract_interface` — returns both the modified class and the new interface source.

### M14 — Extract Superclass

`JdtExtractSuperclass.extractSuperclass(classSource, unitName, superclassName, methodNames?)`

Moves selected public non-static methods (with their bodies) into a new `public abstract` superclass and makes the original class extend it. Methods are removed from the subclass — they are inherited. Rejects if the class already extends another class.

CLI: `java-refactor extract-superclass --file F --name N --superclass-file PATH [--methods m1,m2] [--dry-run]`

MCP tool: `extract_superclass` — returns the modified class and the new superclass source.

### M15 — Move Class

`JdtMoveClass.moveClass(project, sourceFile, newPackage)` moves a class to a new package. Updates the `package` declaration, computes the new canonical file path, and updates all explicit single-class imports in the project. Returns `Result(newClassSource, newFilePath, changedImports)` — the caller writes the new file, updates imports, and deletes the original.

CLI: `java-refactor move-class --file F --package com.example.util [--project P] [--dry-run]`

MCP tool: `move_class` — returns new source, new path, and updated import files. Does not write to disk.

**Limitations:** same-package references (no explicit import), wildcard imports, and fully-qualified type references in source code are not updated.

### M16 — Rename Package

`JdtRenamePackage.renamePackage(project, oldPackage, newPackage)` renames a package across the project. Updates `package` declarations in every file in the old package (including sub-packages), computes new file paths, and updates all single-class and wildcard imports in the whole project. Returns `Result(List<FileChange>)` — caller writes files and deletes originals.

CLI: `java-refactor rename-package --old-package com.example.service --new-package com.example.util [--project P] [--dry-run]`

MCP tool: `rename_package` — returns new source and new path for every changed file. Does not write to disk.

**Limitation:** fully-qualified type references in source code are not updated.

### M17 — Pull Up / Push Down Method

`JdtPullUpMethod.pullUp(project, sourceFile, offset)` moves a method declaration from a
subclass to its direct superclass. The superclass is located by simple name within the
project source roots.

`JdtPushDownMethod.pushDown(project, sourceFile, offset)` moves a method from a class down
to all direct subclasses found in the project (discovered by scanning for `extends ClassName`).
Both operations return `Map<Path, String>` — the caller writes the changed files.

CLI:
```
java-refactor pull-up  --file Dog.java     --line 9 --column 17 [--project P] [--dry-run]
java-refactor push-down --file Shape.java  --line 4 --column 19 [--project P] [--dry-run]
```

MCP tools: `pull_up_method`, `push_down_method` — accept `project_root`, `file`, `line`,
`column`; return new source for every changed file. Do not write to disk.

**Preconditions checked:**
- Pull up: class has an explicit `extends` clause; superclass source is in the project;
  superclass does not already have a method with the same name and parameter count.
- Push down: at least one direct subclass exists in the project; no subclass already
  declares the method.

**Limitations:** fully-qualified `extends` clauses (e.g. `extends com.example.Foo`) are not
matched when scanning for subclasses; `@Override` annotations are copied verbatim.

## Integration

### 1. Build the MCP server jar

```bash
mvn package -DskipTests
```

The fat jar is at:

```
refactor-mcp/target/refactor-mcp-0.1.0-SNAPSHOT.jar
```

Use the absolute path to this jar in every config below.

---

### 2. Wire it into your AI coding tool

#### Claude Code (CLI / claude.ai/code)

One-liner:

```bash
claude mcp add java-refactoring -- java -jar /absolute/path/to/refactor-mcp-0.1.0-SNAPSHOT.jar
```

Or add it manually to `.claude/settings.json` (project-scoped) or `~/.claude/settings.json` (global):

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/refactor-mcp-0.1.0-SNAPSHOT.jar"]
    }
  }
}
```

#### OpenCode

Add to `~/.config/opencode.json`:

```json
{
  "mcp": {
    "java-refactoring": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/refactor-mcp-0.1.0-SNAPSHOT.jar"]
    }
  }
}
```

#### Cursor / Windsurf / other MCP-compatible editors

Most editors that support MCP use the same `mcpServers` schema. Add to the editor's MCP config file (e.g. `.cursor/mcp.json`):

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/refactor-mcp-0.1.0-SNAPSHOT.jar"]
    }
  }
}
```

---

### 3. How to use it in a conversation

The agent sees three tools once the server is connected:

| Tool | Purpose |
|------|---------|
| `list_refactorings` | Enumerate available operations |
| `analyze_refactoring` | Preview changes — returns new source, writes nothing |
| `apply_refactoring` | Apply changes and write to disk |

Every call to `analyze_refactoring` / `apply_refactoring` requires:

| Parameter | Example |
|-----------|---------|
| `project_root` | `/home/me/myproject` (Maven root — contains `pom.xml`) |
| `file` | `src/main/java/com/example/Calculator.java` |
| `line` | `4` |
| `column` | `16` |
| `refactoring` | `rename` |
| `new_name` | `plus` |

**Example prompts you can give the agent:**

```
Rename the method `add` in Calculator.java at line 4 to `plus`.
Use the java-refactoring tool. Project root is /home/me/myproject.
Preview first, then apply.
```

```
Extract lines 10–14 of Calculator.java into a private method called `validate`.
Project root: /home/me/myproject.
```

```
Inline the variable `result` declared at line 8 of App.java.
Project root: /home/me/myproject.
```

A well-prompted agent will call `analyze_refactoring` first (dry-run), show you the diff, and only call `apply_refactoring` after your confirmation.

---

## Planned

| Milestone | Description |
|-----------|-------------|
| M18+ | pull up / push down field, abstract method, … |

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
