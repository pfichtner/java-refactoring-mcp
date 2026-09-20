package com.github.pfichtner.mcp;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
        assertThat(resultByName.size()).as("Same number of changed files expected").isEqualTo(resultByPosition.size());
        for (int i = 0; i < resultByPosition.size(); i++) {
            var fcByPos  = resultByPosition.get(i);
            var fcByName = resultByName.get(i);
            assertThat(fcByName.oldPath()).as("Path mismatch for entry " + i).isEqualTo(fcByPos.oldPath());
            assertThat(fcByName.newSource()).as("Content mismatch for " + fcByPos.oldPath().getFileName()).isEqualTo(fcByPos.newSource());
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

        assertThat(Files.readString(calcFile)).as("analyze (no write) must not modify files on disk").isEqualTo(before);
    }

    // -------------------------------------------------------------------------
    // Error cases — buildLocatorFromArgs validation
    // -------------------------------------------------------------------------

    @Test
    void no_locator_at_all_throws() {
        Map<String, Object> empty = new java.util.HashMap<>();
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> RefactoringServer.resolveOffset(empty, "class Foo {}", "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("Specify either");
    }

    @Test
    void position_and_name_together_throws() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("line", 1); args.put("column", 1); args.put("method", "foo");
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> RefactoringServer.resolveOffset(args, "class Foo { void foo() {} }", "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("not both");
    }

    @Test
    void only_column_without_line_throws() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("column", 7);
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> RefactoringServer.resolveOffset(args, "class Foo {}", "Foo.java")).actual();
        assertThat(ex.getMessage()).containsIgnoringCase("line");
    }

    @Test
    void line_without_column_resolves_single_element() throws Exception {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("line", 2);
        // line 2 has exactly one named declaration: the field "count"
        String source = "class Counter {\n    int count;\n}";
        int offset = RefactoringServer.resolveOffset(args, source, "Counter.java");
        assertThat(source.substring(offset, offset + 5)).isEqualTo("count");
    }

    @Test
    void line_without_column_ambiguous_throws() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("line", 3);
        // line 3 has two variable declarations: i and j
        String source = "class C {\n    void m() {\n        int i=0, j=0;\n    }\n}";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> RefactoringServer.resolveOffset(args, source, "C.java")).actual();
        assertThat(ex.getMessage()).containsIgnoringCase("ambiguous");
    }

    @Test
    void parameter_without_method_throws() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("parameter", "unused");
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> RefactoringServer.resolveOffset(
                args, "class Foo { void bar(int unused) {} }", "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("'method'");
    }

    @Test
    void two_name_kinds_together_throws() {
        Map<String, Object> args = new java.util.HashMap<>();
        args.put("method", "foo"); args.put("field", "bar");
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> RefactoringServer.resolveOffset(
                args, "class Foo { void foo() {} int bar; }", "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("only one");
    }
}
