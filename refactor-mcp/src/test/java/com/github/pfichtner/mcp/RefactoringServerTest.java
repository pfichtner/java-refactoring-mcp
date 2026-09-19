package com.github.pfichtner.mcp;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
    void list_refactorings_returns_available_operations() {
        var result = RefactoringServer.listRefactorings()
                .callHandler()
                .apply(null, fakeRequest(Map.of()));

        assertFalse(result.isError());
        String text = textOf(result);
        assertTrue(text.contains("rename"), "Should mention 'rename'");
        assertTrue(text.contains("project_root"), "Should describe required args");
    }

    // -------------------------------------------------------------------------
    // analyze_refactoring (dry-run)
    // -------------------------------------------------------------------------

    @Test
    void analyze_refactoring_rename_method_preview() throws Exception {
        Map<String, Object> args = renameMethodArgs();

        var changed = RefactoringServer.executeRename(args);
        String preview = RefactoringServer.formatPreview(changed);

        Approvals.verify(preview);
    }

    @Test
    void analyze_refactoring_does_not_write_files() throws Exception {
        Path calcFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Calculator.java");
        String before = Files.readString(calcFile);

        RefactoringServer.executeRename(renameMethodArgs());

        assertEquals(before, Files.readString(calcFile),
                "analyze must not modify files on disk");
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

        var changed = RefactoringServer.executeRename(args);
        for (var entry : changed.entrySet()) {
            Files.writeString(entry.getKey(), entry.getValue());
        }
        String summary = RefactoringServer.formatSummary(changed);

        Approvals.verify(summary);

        // Verify disk state
        String calc = Files.readString(tmp.resolve("src/main/java/com/example/Calculator.java"));
        assertTrue(calc.contains("plus"), "Calculator.java should contain 'plus'");
        assertFalse(calc.contains(" add("), "Calculator.java should not contain 'add'");

        String app = Files.readString(tmp.resolve("src/main/java/com/example/App.java"));
        assertTrue(app.contains(".plus("), "App.java call site should be updated");
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

        var ex = assertThrows(IllegalArgumentException.class,
                () -> RefactoringServer.executeRename(args));
        assertTrue(ex.getMessage().contains("extract_method"));
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

    private static io.modelcontextprotocol.spec.McpSchema.CallToolRequest fakeRequest(
            Map<String, Object> arguments) {
        return io.modelcontextprotocol.spec.McpSchema.CallToolRequest.builder()
                .name("fake")
                .arguments(arguments)
                .build();
    }

    private static String textOf(io.modelcontextprotocol.spec.McpSchema.CallToolResult result) {
        return ((io.modelcontextprotocol.spec.McpSchema.TextContent)
                result.content().get(0)).text();
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
