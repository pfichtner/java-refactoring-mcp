# java-refactoring-mcp

**The IDE your coding agent never had** — semantic Java refactoring for AI agents.

AI coding agents are great at reading and generating Java — but they edit source files as plain text. When you ask an agent to rename a method, it searches for strings; when you ask it to move a class, it guesses import paths. On small files this works. On real projects it breaks.

**java-refactoring-mcp** gives coding agents a set of proper refactoring tools backed by [Eclipse JDT](https://eclipse.dev/jdt/) — the same engine IntelliJ and Eclipse IDEs use. The agent calls a tool, JDT resolves every binding across every source file, and the correct minimal diff is returned. No grep. No regex. No missed call sites.

---

## What it can do

| Refactoring | Scope |
|-------------|-------|
| Rename symbol | Local / parameter (file), field / method / type / package (project-wide) |
| Extract method | Single file |
| Inline method | Project-wide |
| Extract / inline variable | Single file |
| Extract constant | Single file |
| Introduce / remove parameter | Project-wide (all call sites) |
| Extract interface | Single file |
| Extract superclass | Single file |
| Move class to new package | Project-wide (imports updated) |
| Pull up / push down method | Superclass ↔ subclasses |
| Pull up / push down field | Superclass ↔ subclasses |
| Introduce static factory | Project-wide (all `new` call sites) |
| Introduce parameter object | Project-wide |
| Convert class to record | Project-wide (accessor call sites renamed) |
| Change method signature (reorder params / return type) | Project-wide for param reorder; declaration-only for return type |
| Encapsulate field (getter + optional setter) | Project-wide (all read/write access sites rewritten) |
| Decompose conditional | Single file (extracts condition into named boolean method) |

All operations follow **analyze → apply**: the agent can preview the exact diff before writing anything to disk.

---

## Quick start

### 1. Prerequisites

- Java 21+
- Maven 3.8+

### 2. Get the MCP server jar

Either download the prebuilt jar from the [latest release](https://github.com/pfichtner/java-refactoring-mcp/releases) — no build needed — or build it yourself:

```bash
git clone https://github.com/pfichtner/java-refactoring-mcp
cd java-refactoring-mcp
mvn package -DskipTests
```

The fat jar lands at:

```
refactor-mcp/target/refactor-mcp-<version>-fat.jar
```

Note the absolute path — you will need it in the next step.

### 3. Wire it into your coding agent

#### Claude Code

```bash
claude mcp add java-refactoring -- java -jar /absolute/path/to/refactor-mcp-0.1.0-SNAPSHOT-fat.jar
```

Or add it to `.claude/settings.json` (project-scoped) or `~/.claude/settings.json` (global):

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/refactor-mcp-0.1.0-SNAPSHOT-fat.jar"]
    }
  }
}
```

#### Cursor / Windsurf / other MCP editors

Add to the editor's MCP config file (e.g. `.cursor/mcp.json`):

```json
{
  "mcpServers": {
    "java-refactoring": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/refactor-mcp-0.1.0-SNAPSHOT-fat.jar"]
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
      "args": ["-jar", "/absolute/path/to/refactor-mcp-0.1.0-SNAPSHOT-fat.jar"]
    }
  }
}
```

---

## How to use it — prompting your agent

Once connected the agent sees three tools:

| Tool | What it does |
|------|--------------|
| `list_refactorings` | Lists every available operation |
| `analyze_refactoring` | Dry-run — returns the new source for every changed file, writes nothing |
| `apply_refactoring` | Applies the changes and writes to disk |

A well-prompted agent will always call `analyze_refactoring` first, show you the diff, and only call `apply_refactoring` after your confirmation.

### Giving your agent standing instructions (skill file)

Repeating "use the MCP tools, never edit `.java` files directly" in every prompt is error-prone — the agent can forget or fall back to text edits on complex tasks.
A better approach is a **skill file**: a named instruction set the agent loads once and follows throughout the session.

**Claude Code** — create `.claude/skills/java-refactor.md` in your project:

```markdown
---
description: Use java-refactoring MCP for all Java changes. Enforces analyze → apply and blocks raw text edits on .java files.
---

# Java Refactoring

The `java-refactoring` MCP server is connected. Use it for **every change to a `.java` file**.
Never use Write, Edit, or shell tools (sed, awk, …) to modify Java source directly — those treat
code as text and miss bindings, overloads, and cross-file references.

## Workflow

1. **Discover** — call `list_refactorings` if unsure which operation fits
2. **Preview** — call `analyze_refactoring` (dry-run) to verify the diff before writing anything
3. **Apply** — call the operation tool; one refactoring at a time

## Rules

- Prefer name-based locators (`"method": "add"`) over line/column — they survive edits
- For overloaded methods include param types: `"method": "add(int, int)"`
- If no tool covers the needed change, say so and ask rather than falling back to text edits
```

Then invoke it at the start of a refactoring session:

```
/java-refactor
Rename the method `add` in Calculator.java to `plus`. Project root: /home/me/myproject.
```

The agent loads the skill instructions, then proceeds with `analyze_refactoring` → confirm → `apply_refactoring` without needing further reminders.

Other editors that support system-prompt injection (Cursor, Windsurf, OpenCode, etc.) can embed the same rules as a project-level system prompt in their config.

### Example prompts

```
Rename the method `add` in Calculator.java at line 4 to `plus`.
Use the java-refactoring MCP tool. Project root is /home/me/myproject.
Preview the changes first, then apply.
```

```
Extract lines 10–14 of Service.java into a private method called `validate`.
Project root: /home/me/myproject.
```

```
Inline the variable `result` declared at line 8 of App.java.
Project root: /home/me/myproject.
```

```
Move class com.example.UserDto to the package com.example.dto.
Project root: /home/me/myproject. Preview first.
```

```
Convert the Point class in Point.java to a Java record.
Project root: /home/me/myproject.
```

Every tool call requires:

| Parameter | Example |
|-----------|---------|
| `project_root` | `/home/me/myproject` — project root (auto-detects Maven `pom.xml` or Gradle `build.gradle`/`build.gradle.kts`) |
| `file` | `src/main/java/com/example/Calculator.java` |
| `refactoring` | `rename` |
| locator (see below) | identifies the target element |
| additional params | depend on the refactoring (e.g. `new_name`, `method_name`) |

### Locating elements — position or name

All point-based operations accept **either** a position-based locator **or** a name-based locator:

**Position (classic)** — supply `line` + `column`:
```json
{ "line": 4, "column": 16 }
```

**By name (preferred for agents)** — no prior file read needed; stable across edits:
```json
{ "method": "add" }
{ "method": "add(int, int)" }
{ "field":  "amount" }
{ "type":   "OrderService" }
```

For overloaded methods, include param types in parentheses: `"add(int, int)"`.
When a file contains multiple types, add `"class": "TypeName"` to scope the search.
For `remove_param`, combine `"method"` + `"parameter"`:
```json
{ "method": "process", "parameter": "unused" }
```

Range-based operations (`extract_method`, `extract_variable`, etc.) continue to use
`start_line`/`start_column`/`end_line`/`end_column` — a selection range cannot be expressed as a name.

---

## Why JDT over text edits?

| Problem | Text edit | JDT |
|---------|-----------|-----|
| Rename method used in 12 files | Misses overloaded names, string literals, comments | Resolves the binding; touches only real references |
| Move class to new package | May miss wildcard imports or fully-qualified usages | Updates all single-class imports project-wide |
| Extract method with local variables | Hard to track data-flow | JDT computes parameters and return type from the AST |
| Inline method across files | Agent may not find all call sites | Binding key matches every call site exactly |

---

## CLI (optional)

The same engine is also available as a standalone command-line tool — useful for scripting or CI:

```bash
# By position (classic)
java-refactor rename \
  --file src/main/java/com/example/Calculator.java \
  --line 4 --column 16 \
  --name plus \
  --dry-run

# By name (no prior line lookup needed)
java-refactor rename \
  --file src/main/java/com/example/Calculator.java \
  --method add \
  --name plus \
  --dry-run

# Overloaded method — disambiguate with param types
java-refactor rename \
  --file src/main/java/com/example/Calculator.java \
  "--method" "add(int,int)" \
  --name plus
```

All point-based subcommands accept `--method`, `--field`, `--type` as alternatives to `--line`/`--column`.
For `remove-param`, use `--method <name> --parameter <paramName>`.

Run `java-refactor --help` for the full list of subcommands.

---

## Further reading

- [REFERENCE.md](REFERENCE.md) — full per-operation API, parameter tables, known limitations, design principles, and evaluation data
- [APPROVALS.md](APPROVALS.md) — index of all golden-master test storyboards (input → refactoring → output)
