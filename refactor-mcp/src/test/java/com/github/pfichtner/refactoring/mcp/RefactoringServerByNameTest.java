package com.github.pfichtner.refactoring.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.locator.LocatorResolver;

/**
 * Integration tests for name-based locator support in the MCP layer.
 *
 * Tests that MCP tool handlers accept {@code method}/{@code field}/{@code type}
 * fields as alternatives to {@code line}/{@code column}, and that error cases
 * (missing locator, conflicting locators) produce clear error messages.
 */
class RefactoringServerByNameTest {

    private static final Path FIXTURE_ROOT;
    private static final Path TYPE_FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    RefactoringServerByNameTest.class.getClassLoader()
                            .getResource("fixtures/projects/rename-method/pom.xml")
                            .toURI()
            ).getParent();
            TYPE_FIXTURE_ROOT = Path.of(
                    RefactoringServerByNameTest.class.getClassLoader()
                            .getResource("fixtures/projects/rename-type/pom.xml")
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
        Map<String, Object> common = Map.of(
                "project_root", FIXTURE_ROOT.toString(),
                "file",         "src/main/java/com/example/Calculator.java",
                "new_name",     "plus"
        );
        Map<String, Object> byPosition = new HashMap<>(common);
        byPosition.putAll(Map.of("line", 4, "column", 16));
        Map<String, Object> byName = new HashMap<>(common);
        byName.put("method", "add");

        var resultByPosition = RenameTool.rename(new Options.Reader(byPosition));
        var resultByName     = RenameTool.rename(new Options.Reader(byName));

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
                "new_name",     "plus"
        );

        var changed = RenameTool.rename(new Options.Reader(args));
        String preview = ToolSupport.formatDryrun(changed);

        Approvals.verify(preview);
    }

    @Test
    void rename_type_by_type_name_produces_same_result_as_position() throws Exception {
        Map<String, Object> common = Map.of(
                "project_root", TYPE_FIXTURE_ROOT.toString(),
                "file",         "src/main/java/com/example/Rectangle.java",
                "new_name",     "Rect"
        );
        // line 3 col 14 is the 'R' of `public class Rectangle`
        Map<String, Object> byPosition = new HashMap<>(common);
        byPosition.putAll(Map.of("line", 3, "column", 14));
        Map<String, Object> byName = new HashMap<>(common);
        byName.put("type", "Rectangle");

        var resultByPosition = RenameTool.rename(new Options.Reader(byPosition));
        var resultByName     = RenameTool.rename(new Options.Reader(byName));

        // Both approaches should produce identical output for every changed file
        assertThat(resultByName.size()).as("Same number of changed files expected").isEqualTo(resultByPosition.size());
        for (int i = 0; i < resultByPosition.size(); i++) {
            var fcByPos  = resultByPosition.get(i);
            var fcByName = resultByName.get(i);
            assertThat(fcByName.oldPath()).as("Path mismatch for entry " + i).isEqualTo(fcByPos.oldPath());
            assertThat(fcByName.newPath()).as("New path mismatch for entry " + i).isEqualTo(fcByPos.newPath());
            assertThat(fcByName.newSource()).as("Content mismatch for " + fcByPos.oldPath().getFileName()).isEqualTo(fcByPos.newSource());
        }
    }

    @Test
    void analyze_rename_by_type_name_preview() throws Exception {
        Map<String, Object> args = Map.of(
                "project_root", TYPE_FIXTURE_ROOT.toString(),
                "file",         "src/main/java/com/example/Rectangle.java",
                "type",         "Rectangle",
                "new_name",     "Rect"
        );

        var changed = RenameTool.rename(new Options.Reader(args));
        String preview = ToolSupport.formatDryrun(changed);

        Approvals.verify(preview);
    }

    // -------------------------------------------------------------------------
    // Error cases — buildLocatorFromArgs validation
    // -------------------------------------------------------------------------

    @Test
    void no_locator_at_all_throws() {
        Map<String, Object> empty = Map.of();
        String source = "class Foo {}";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> LocatorResolver.resolve(new Options.Reader(empty).getLocator(), source, "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("Specify either");
    }

    @Test
    void position_and_name_agree_returns_identifier_offset() throws Exception {
        // col 18 lands on 'f' of 'foo' — same element the name locator finds
        Map<String, Object> args = Map.of("line", 1, "column", 18, "method", "foo");
        String source = "class Foo { void foo() {} }";
        int offset = LocatorResolver.resolve(new Options.Reader(args).getLocator(), source, "Foo.java");
        assertThat(source.substring(offset, offset + 3)).isEqualTo("foo");
    }

    @Test
    void position_and_name_disagree_throws_descriptive_mismatch() {
        // col 1 lands on 'c' of 'class', but method="foo" points elsewhere
        Map<String, Object> args = Map.of("line", 1, "column", 1, "method", "foo");
        String source = "class Foo { void foo() {} }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LocatorResolver.resolve(new Options.Reader(args).getLocator(), source, "Foo.java"))
                .actual();
        assertThat(ex.getMessage()).contains("disagree");
        assertThat(ex.getMessage()).contains("\"foo\"");
    }

    @Test
    void only_column_without_line_throws() {
        Map<String, Object> args = Map.of("column", 7);
        String source = "class Foo {}";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LocatorResolver.resolve(new Options.Reader(args).getLocator(), source, "Foo.java")).actual();
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
        int offset = LocatorResolver.resolve(new Options.Reader(args).getLocator(), source, "Counter.java");
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
                .isThrownBy(() -> LocatorResolver.resolve(new Options.Reader(args).getLocator(), source, "C.java")).actual();
        assertThat(ex.getMessage()).containsIgnoringCase("ambiguous");
    }

    @Test
    void parameter_without_method_throws() {
        Map<String, Object> args = Map.of("parameter", "unused");
        String source = "class Foo { void bar(int unused) {} }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> LocatorResolver.resolve(new Options.Reader(args).getLocator(), source, "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("'method'");
    }

    @Test
    void two_name_kinds_together_throws() {
        Map<String, Object> args = Map.of("method", "foo", "field", "bar");
        String source = "class Foo { void foo() {} int bar; }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> LocatorResolver.resolve(new Options.Reader(args).getLocator(), source, "Foo.java")).actual();
        assertThat(ex.getMessage()).contains("only one");
    }

}
