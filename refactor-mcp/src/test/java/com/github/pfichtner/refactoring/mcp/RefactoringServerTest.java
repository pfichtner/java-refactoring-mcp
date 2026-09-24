
package com.github.pfichtner.refactoring.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Tests for the MCP tool handlers.
 *
 * Handlers are tested directly (no live MCP connection needed) — the MCP
 * transport layer is a thin wrapper that doesn't need exercising here.
 */
class RefactoringServerTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    RefactoringServerTest.class.getClassLoader()
                            .getResource("fixtures/projects/rename-method/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // -------------------------------------------------------------------------
    // list_refactorings
    // -------------------------------------------------------------------------

    @Test
    void list_refactorings_covers_every_registered_refactoring() {
        var result = ListRefactoringsTool.listRefactorings()
                .callHandler()
                .apply(null, fakeRequest(Map.of()));

        assertThat(result.isError()).isFalse();
        String text = textOf(result);

        Set<String> registered = RefactoringServer.TOOLS.stream()
                .map(spec -> spec.tool().name())
                .filter(name -> !RefactoringServer.META_TOOLS.contains(name))
                .collect(Collectors.toSet());

        assertThat(topLevelNames(text)).isEqualTo(registered);
    }

    @Test
    void list_refactorings_golden_master() {
        var result = ListRefactoringsTool.listRefactorings()
                .callHandler()
                .apply(null, fakeRequest(Map.of()));

        Approvals.verify(textOf(result));
    }

    /** Lines that start a tool block: non-empty, un-indented, and not the heading. */
    private static Set<String> topLevelNames(String text) {
        return Arrays.stream(text.split("\n"))
                .filter(line -> !line.isBlank() && !line.startsWith(" "))
                .filter(line -> !line.startsWith("Available refactorings"))
                .map(String::strip)
                .collect(Collectors.toSet());
    }

    // -------------------------------------------------------------------------
    // analyze_refactoring (dry-run)
    // -------------------------------------------------------------------------

    @Test
    void analyze_refactoring_rename_method_preview() throws Exception {
        Map<String, Object> args = renameMethodArgs();

        var changed = ToolSupport.executeRename(new Options.Reader(args));
        String preview = ToolSupport.formatPreview(changed);

        Approvals.verify(preview);
    }

    // -------------------------------------------------------------------------
    // apply_refactoring
    // -------------------------------------------------------------------------

    @Test
    void apply_refactoring_writes_files_and_returns_summary(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws Exception {
        // Copy the fixture project so the test is non-destructive
        copyTree(FIXTURE_ROOT, tmp);

        Map<String, Object> args = Map.of(
                "project_root", tmp.toString(),
                "file",         "src/main/java/com/example/Calculator.java",
                "line",         4, "column", 16,
                "refactoring",  "rename",
                "new_name",     "plus"
        );

        var changed = ToolSupport.executeRename(new Options.Reader(args));
        for (var fc : changed) {
            Files.createDirectories(fc.newPath().getParent());
            Files.writeString(fc.newPath(), fc.newSource());
            if (fc.pathChanged()) Files.deleteIfExists(fc.oldPath());
        }
        String summary = ToolSupport.formatSummary(changed);

        Approvals.verify(summary);

        // Verify disk state
        String calc = Files.readString(tmp.resolve("src/main/java/com/example/Calculator.java"));
        assertThat(calc).as("Calculator.java should contain 'plus'").contains("plus");
        assertThat(calc).as("Calculator.java should not contain 'add'").doesNotContain(" add(");

        String app = Files.readString(tmp.resolve("src/main/java/com/example/App.java"));
        assertThat(app).as("App.java call site should be updated").contains(".plus(");
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void unsupported_refactoring_type_returns_error() throws Exception {
        Map<String, Object> args = Map.of(
                "project_root", FIXTURE_ROOT.toString(),
                "file",         "src/main/java/com/example/Calculator.java",
                "line", 4, "column", 16,
                "refactoring", "extract_method",
                "new_name", "helper"
        );

        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> ToolSupport.executeRename(new Options.Reader(args))).actual();
        assertThat(ex.getMessage()).contains("extract_method");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Map<String, Object> renameMethodArgs() {
        return Map.of(
                "project_root", FIXTURE_ROOT.toString(),
                "file",         "src/main/java/com/example/Calculator.java",
                "line",         4, "column", 16,
                "refactoring",  "rename",
                "new_name",     "plus"
        );
    }

    private static McpSchema.CallToolRequest fakeRequest(
            Map<String, Object> arguments) {
        return McpSchema.CallToolRequest.builder("fake")
                .arguments(arguments)
                .build();
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().get(0)).text();
    }

    private static void copyTree(Path src, Path dst) throws Exception {
        try (var stream = Files.walk(src)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                Path target = dst.resolve(src.relativize(p));
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
    }
}
