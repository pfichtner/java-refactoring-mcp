package com.github.pfichtner.refactoring.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;
import java.util.Map;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

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

        var resultByPosition = RefactoringServer.executeRename(new Options.Reader(byPosition));
        var resultByName     = RefactoringServer.executeRename(new Options.Reader(byName));

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

        var changed = RefactoringServer.executeRename(new Options.Reader(args));
        String preview = RefactoringServer.formatPreview(changed);

        Approvals.verify(preview);
    }

    // -------------------------------------------------------------------------
    // Error cases — buildLocatorFromArgs validation
    // -------------------------------------------------------------------------

    @Test
    void no_locator_at_all_throws() {
        Map<String, Object> empty = Map.of();
        String source = "class Foo {}";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> RefactoringServer.resolveOffset(new Options.Reader(empty), source, "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("Specify either");
    }

    @Test
    void position_and_name_together_throws() {
        Map<String, Object> args = Map.of("line", 1, "column", 1, "method", "foo");
        String source = "class Foo { void foo() {} }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> RefactoringServer.resolveOffset(new Options.Reader(args), source, "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("not both");
    }

    @Test
    void only_column_without_line_throws() {
        Map<String, Object> args = Map.of("column", 7);
        String source = "class Foo {}";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> RefactoringServer.resolveOffset(new Options.Reader(args), source, "Foo.java")).actual();
        assertThat(ex.getMessage()).containsIgnoringCase("line");
    }

    @Test
    void line_without_column_resolves_single_element() throws Exception {
        Map<String, Object> args = Map.of("line", 2);
        // line 2 has exactly one named declaration: the field "count"
        String source = """
			class Counter {
			    int count;
			}""";
        int offset = RefactoringServer.resolveOffset(new Options.Reader(args), source, "Counter.java");
        assertThat(source.substring(offset, offset + 5)).isEqualTo("count");
    }

    @Test
    void line_without_column_ambiguous_throws() {
        Map<String, Object> args = Map.of("line", 3);
        // line 3 has two variable declarations: i and j
        String source = """
			class C {
			    void m() {
			        int i=0, j=0;
			    }
			}""";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> RefactoringServer.resolveOffset(new Options.Reader(args), source, "C.java")).actual();
        assertThat(ex.getMessage()).containsIgnoringCase("ambiguous");
    }

    @Test
    void parameter_without_method_throws() {
        Map<String, Object> args = Map.of("parameter", "unused");
        String source = "class Foo { void bar(int unused) {} }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> RefactoringServer.resolveOffset(
		        new Options.Reader(args), source, "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("'method'");
    }

    @Test
    void two_name_kinds_together_throws() {
        Map<String, Object> args = Map.of("method", "foo", "field", "bar");
        String source = "class Foo { void foo() {} int bar; }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> RefactoringServer.resolveOffset(
		        new Options.Reader(args), source, "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("only one");
    }

}
