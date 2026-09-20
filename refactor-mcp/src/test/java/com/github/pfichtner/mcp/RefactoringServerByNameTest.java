package com.github.pfichtner.mcp;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for name-based locator support in the MCP layer.
 *
 * Tests that MCP tool handlers accept {@code method}/{@code field}/{@code type}
 * fields as alternatives to {@code line}/{@code column}, and that error cases
 * (missing locator, conflicting locators) produce clear error messages.
 */
class RefactoringServerByNameTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    RefactoringServerByNameTest.class.getClassLoader()
                            .getResource("fixtures/projects/rename-method/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // -------------------------------------------------------------------------
    // Happy paths
    // -------------------------------------------------------------------------

    @Test
    void rename_by_method_name_produces_same_result_as_position() throws Exception {
        Map<String, Object> byPosition = Map.of(
                "project_root", FIXTURE_ROOT.toString(),
                "file",         "src/main/java/com/example/Calculator.java",
                "line",         4, "column", 16,
                "refactoring",  "rename",
                "new_name",     "plus"
        );
        Map<String, Object> byName = Map.of(
                "project_root", FIXTURE_ROOT.toString(),
                "file",         "src/main/java/com/example/Calculator.java",
                "method",       "add",
                "refactoring",  "rename",
                "new_name",     "plus"
        );

        var resultByPosition = RefactoringServer.executeRename(byPosition);
        var resultByName     = RefactoringServer.executeRename(byName);

        // Both approaches should produce identical output for every changed file
        assertEquals(resultByPosition.size(), resultByName.size(),
                "Same number of changed files expected");
        for (Path p : resultByPosition.keySet()) {
            assertEquals(resultByPosition.get(p), resultByName.get(p),
                    "Content mismatch for " + p.getFileName());
        }
    }

    @Test
    void analyze_rename_by_method_name_preview() throws Exception {
        Map<String, Object> args = Map.of(
                "project_root", FIXTURE_ROOT.toString(),
                "file",         "src/main/java/com/example/Calculator.java",
                "method",       "add",
                "refactoring",  "rename",
                "new_name",     "plus"
        );

        var changed = RefactoringServer.executeRename(args);
        String preview = RefactoringServer.formatPreview(changed);

        Approvals.verify(preview);
    }

    @Test
    void analyze_rename_by_method_name_does_not_write_files() throws Exception {
        Path calcFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Calculator.java");
        String before = Files.readString(calcFile);

        RefactoringServer.executeRename(Map.of(
                "project_root", FIXTURE_ROOT.toString(),
                "file",         "src/main/java/com/example/Calculator.java",
                "method",       "add",
                "refactoring",  "rename",
                "new_name",     "plus"
        ));

        assertEquals(before, Files.readString(calcFile),
                "analyze (no write) must not modify files on disk");
    }

    // -------------------------------------------------------------------------
    // Error cases — buildLocatorFromArgs validation
    // -------------------------------------------------------------------------

    @Test
    void no_locator_at_all_throws() {
        Map<String, Object> empty = new java.util.HashMap<>();
        var ex = assertThrows(IllegalArgumentException.class,
                () -> RefactoringServer.resolveOffset(empty, "class Foo {}", "Foo.java"));
        assertTrue(ex.getMessage().contains("Specify either"), ex.getMessage());
    }

    @Test
    void position_and_name_together_throws() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("line", 1); args.put("column", 1); args.put("method", "foo");
        var ex = assertThrows(IllegalArgumentException.class,
                () -> RefactoringServer.resolveOffset(args, "class Foo { void foo() {} }", "Foo.java"));
        assertTrue(ex.getMessage().contains("not both"), ex.getMessage());
    }

    @Test
    void only_line_without_column_throws() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("line", 1);
        var ex = assertThrows(IllegalArgumentException.class,
                () -> RefactoringServer.resolveOffset(args, "class Foo {}", "Foo.java"));
        assertTrue(ex.getMessage().toLowerCase().contains("column"), ex.getMessage());
    }

    @Test
    void parameter_without_method_throws() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("parameter", "unused");
        var ex = assertThrows(IllegalArgumentException.class,
                () -> RefactoringServer.resolveOffset(
                        args, "class Foo { void bar(int unused) {} }", "Foo.java"));
        assertTrue(ex.getMessage().contains("'method'"), ex.getMessage());
    }

    @Test
    void two_name_kinds_together_throws() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("method", "foo"); args.put("field", "bar");
        var ex = assertThrows(IllegalArgumentException.class,
                () -> RefactoringServer.resolveOffset(
                        args, "class Foo { void foo() {} int bar; }", "Foo.java"));
        assertTrue(ex.getMessage().contains("only one"), ex.getMessage());
    }
}
