
package com.github.pfichtner.refactoring.mcp;

import static org.assertj.core.api.Assertions.assertThat;

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
                .filter(name -> !ListRefactoringsTool.META_TOOLS.contains(name))
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
    // rename tool (preview + apply)
    // -------------------------------------------------------------------------

    @Test
    void rename_returns_preview() {
        Map<String, Object> args = renameMethodArgs();

        var result = RenameTool.rename().callHandler()
                .apply(null, fakeRequest(args));

        assertThat(result.isError()).isFalse();
        Approvals.verify(textOf(result));
    }

    @Test
    void rename_with_apply_writes_files_and_returns_summary(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws Exception {
        // Copy the fixture project so the test is non-destructive
        copyTree(FIXTURE_ROOT, tmp);

        Map<String, Object> args = Map.of(
                "project_root", tmp.toString(),
                "file",         "src/main/java/com/example/Calculator.java",
                "line",         4, "column", 16,
                "new_name",     "plus"
        );

        var result = RenameTool.rename().callHandler()
                .apply(null, fakeRequest(args));

        assertThat(result.isError()).isFalse();
        Approvals.verify(textOf(result));

        // Structure-only disk verification: both changed files exist
        assertThat(tmp.resolve("src/main/java/com/example/Calculator.java")).exists();
        assertThat(tmp.resolve("src/main/java/com/example/App.java")).exists();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Map<String, Object> renameMethodArgs() {
        return Map.of(
                "project_root", FIXTURE_ROOT.toString(),
                "file",         "src/main/java/com/example/Calculator.java",
                "line",         4, "column", 16,
                "new_name",     "plus",
                "dryrun",       true
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
