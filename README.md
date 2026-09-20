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

All operations follow **analyze → apply**: the agent can preview the exact diff before writing anything to disk.

---

## Quick start

### 1. Prerequisites

- Java 21+
- Maven 3.8+

### 2. Build the MCP server

```bash
git clone https://github.com/your-org/java-refactoring-mcp
cd java-refactoring-mcp
mvn package -DskipTests
```

The fat jar lands at:

```
refactor-mcp/target/refactor-mcp-0.1.0-SNAPSHOT.jar
```

Note the absolute path — you will need it in the next step.

### 3. Wire it into your coding agent

#### Claude Code

```bash
claude mcp add java-refactoring -- java -jar /absolute/path/to/refactor-mcp-0.1.0-SNAPSHOT.jar
```

Or add it to `.claude/settings.json` (project-scoped) or `~/.claude/settings.json` (global):

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

#### Cursor / Windsurf / other MCP editors

Add to the editor's MCP config file (e.g. `.cursor/mcp.json`):

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

---

## How to use it — prompting your agent

Once connected the agent sees three tools:

| Tool | What it does |
|------|--------------|
| `list_refactorings` | Lists every available operation |
| `analyze_refactoring` | Dry-run — returns the new source for every changed file, writes nothing |
| `apply_refactoring` | Applies the changes and writes to disk |

A well-prompted agent will always call `analyze_refactoring` first, show you the diff, and only call `apply_refactoring` after your confirmation.

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
| `project_root` | `/home/me/myproject` — Maven root (contains `pom.xml`) |
| `file` | `src/main/java/com/example/Calculator.java` |
| `line` | `4` |
| `column` | `16` |
| `refactoring` | `rename` |
| additional params | depend on the refactoring (e.g. `new_name`, `method_name`) |

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
java-refactor rename \
  --file src/main/java/com/example/Calculator.java \
  --line 4 --column 16 \
  --name plus \
  --dry-run
```

Run `java-refactor --help` for the full list of subcommands.

---

## Further reading

- [REFERENCE.md](REFERENCE.md) — full per-operation API, parameter tables, known limitations, design principles, and evaluation data
- [APPROVALS.md](APPROVALS.md) — index of all golden-master test storyboards (input → refactoring → output)
