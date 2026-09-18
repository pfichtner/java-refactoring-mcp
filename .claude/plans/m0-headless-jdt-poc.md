# M0 — Headless JDT Proof of Concept

## Context

This is a greenfield project. The repo contains only `PRD.md` and `CLAUDE.md`. The goal of M0 is to
eliminate architectural risk by proving that a standalone Java process can parse Java source,
resolve semantic bindings via JDT, perform a rename, and verify the result against a golden master
— all without the Eclipse IDE, SWT, or a display server.

## Key Design Decision: JDT Headless Mode

JDT offers two layers:

| Layer | Headless? | What it needs |
|---|---|---|
| `ASTParser` + `ASTRewrite` (AST-only) | Yes — no OSGi required | `jdt.core` + `ecj` |
| `RenameLocalVariableProcessor` (full refactoring) | No — requires `IWorkspace` | OSGi + `core.resources` |

**M0 uses the ASTParser + ASTRewrite path.** This is still fully semantic (JDT resolves variable
bindings, finds all references, and renames them), and it avoids the OSGi workspace setup risk
that belongs in M2. The PRD explicitly says "eliminate architectural risk before building
substantial infrastructure."

M0 will document whether `RenameLocalVariableProcessor` is reachable headlessly as a finding.
The full JDT refactoring processor infrastructure (which needs `IWorkspace`) will be addressed in M2.

## Maven Artifact Versions (verified on Maven Central)

```
org.eclipse.jdt:org.eclipse.jdt.core:3.47.0
org.eclipse.jdt:org.eclipse.jdt.core.manipulation:1.25.0  (pulls in ltk, resources, etc.)
org.eclipse.platform:org.eclipse.core.runtime:3.35.0
org.eclipse.platform:org.eclipse.equinox.common:3.21.0
org.eclipse.platform:org.eclipse.text:3.14.800
```

`org.eclipse.jdt.core` depends on `org.eclipse.core.resources:3.24.100` and
`org.eclipse.ltk.core.refactoring:3.16.0` transitively via `core.manipulation`.

## Project Structure (M0 only)

```
java-refactoring-mcp/
  pom.xml                              ← parent, Java 21, modules: refactor-core
  refactor-core/
    pom.xml
    src/
      main/java/
        dev/mcp/refactor/
          JdtRenamer.java              ← headless rename via ASTParser + ASTRewrite
      test/java/
        dev/mcp/refactor/
          HeadlessJdtRenameTest.java   ← golden-master test
      test/resources/
        fixtures/rename/local-variable/
          input/Foo.java
          approved/Foo.java
```

No CLI or MCP modules yet — those are M4 and M5.

## Files to Create

### `pom.xml` (parent)
- `groupId`: `dev.mcp.refactor`, `artifactId`: `java-refactoring-mcp`, `packaging`: `pom`
- Java 21, UTF-8, JUnit 5 (Jupiter) in dependency management
- Single module: `refactor-core` for now

### `refactor-core/pom.xml`
Dependencies:
- `org.eclipse.jdt:org.eclipse.jdt.core:3.47.0`
- `org.eclipse.platform:org.eclipse.text:3.14.800`
- `org.eclipse.platform:org.eclipse.equinox.common:3.21.0`
- `org.junit.jupiter:junit-jupiter:5.11.x` (test scope)

We do NOT add `core.manipulation` in M0 — that adds transitive weight we don't need yet. We only
add it in M1/M2 when the workspace-based refactoring path is evaluated.

### `JdtRenamer.java`
```
JdtRenamer
  renameLocalVariable(
      String sourceCode,
      String[] classpathEntries,
      String unitName,
      int offset,      ← character offset of the variable reference or declaration
      String newName
  ) -> String          ← transformed source
```

Implementation:
1. `ASTParser parser = ASTParser.newParser(AST.JLS21)`
2. `parser.setEnvironment(classpath, null, null, true)` — enables binding resolution without workspace
3. `parser.setResolveBindings(true)` + `parser.setBindingsRecovery(true)`
4. `parser.setSource(sourceCode.toCharArray())`
5. Traverse AST, find `SimpleName` at offset, get its `IVariableBinding`
6. Collect all `SimpleName` nodes with the same binding key
7. Use `ASTRewrite` + `IDocument` to rename them all
8. Return `document.get()`

This is semantic: it uses JDT's binding resolution to identify the exact symbol and all its
references, not string matching.

### Fixture: `input/Foo.java`
```java
public class Foo {
    public int compute() {
        int x = 6;
        int y = x * 7;
        return y;
    }
}
```
Target: rename local variable `x` (offset 46, line 3) to `answer`.

### Fixture: `approved/Foo.java`
```java
public class Foo {
    public int compute() {
        int answer = 6;
        int y = answer * 7;
        return y;
    }
}
```

### `HeadlessJdtRenameTest.java`
```
- loadFixture(path): String   ← reads from test/resources/fixtures/...
- test_rename_local_variable():
    source = loadFixture("rename/local-variable/input/Foo.java")
    result = JdtRenamer.renameLocalVariable(source, [], "Foo.java", offset_of_x, "answer")
    approved = loadFixture("rename/local-variable/approved/Foo.java")
    assertEquals(approved, result)
```

The test fails if the approved file is wrong or the renamer doesn't work semantically.

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| `ASTParser.setEnvironment()` doesn't resolve bindings without classpath | Low | Add a self-referential classpath entry or compile-only mode |
| Offset calculation in fixture is wrong | Low | Use a helper to find first occurrence of token |
| `ASTRewrite` produces incorrect output (whitespace, etc.) | Low | Golden master catches this immediately |
| M2 will find `RenameLocalVariableProcessor` unusable without OSGi | Medium | ASTRewrite path is the proven fallback |

## Verification

```
mvn -pl refactor-core test
```

Expected: 1 test passes. The golden master comparison must succeed without modifying the approved
fixture. If the output differs, inspect the diff, determine correctness, then update the approved
file manually.

## What M0 Does NOT Do

- No CLI, no MCP
- No Maven project loading (M2)
- No multi-file rename (M3)
- No `RenameLocalVariableProcessor` (M2 evaluates this)
- No commit to git
