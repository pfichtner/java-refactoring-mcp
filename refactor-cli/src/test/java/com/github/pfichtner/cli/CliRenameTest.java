package com.github.pfichtner.cli;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the CLI layer.
 *
 * The CLI is exercised end-to-end: real Picocli argument parsing, real JDT
 * engine, real file I/O (for the apply test). Approved .txt files capture
 * the exact stdout so regressions are caught immediately.
 */
class CliRenameTest {

    // The rename-method fixture project (Calculator.add → plus)
    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliRenameTest.class.getClassLoader()
                            .getResource("fixtures/projects/rename-method/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // -------------------------------------------------------------------------
    // --dry-run
    // -------------------------------------------------------------------------

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path calcFile = FIXTURE_ROOT
                .resolve("src/main/java/com/example/Calculator.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "rename",
                "--file", calcFile.toString(),
                "--line", "4", "--column", "16",
                "--name", "plus",
                "--dry-run");

        assertEquals(0, exit, "Expected exit code 0");
        assertTrue(Files.readString(calcFile).contains("add"),
                "Dry-run must not modify the file on disk");

        Approvals.verify(out.toString());
    }

    // -------------------------------------------------------------------------
    // Apply (writes to a temp copy of the fixture project)
    // -------------------------------------------------------------------------

    @Test
    void apply_writes_changed_files(@TempDir Path tmp) throws Exception {
        // Copy the fixture project into the temp dir so the test is non-destructive
        copyTree(FIXTURE_ROOT, tmp);

        Path calcFile = tmp.resolve("src/main/java/com/example/Calculator.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "rename",
                "--file", calcFile.toString(),
                "--line", "4", "--column", "16",
                "--name", "plus");

        assertEquals(0, exit, "Expected exit code 0");

        // Verify summary output
        Approvals.verify(out.toString());

        // Verify the actual file contents were updated (golden-master the results)
        String updatedCalc = Files.readString(calcFile);
        String updatedApp  = Files.readString(
                tmp.resolve("src/main/java/com/example/App.java"));

        assertFalse(updatedCalc.contains(" add("), "Declaration should be renamed");
        assertTrue(updatedCalc.contains(" plus("), "Declaration should be 'plus'");
        assertFalse(updatedApp.contains(".add("), "Call site should be renamed");
        assertTrue(updatedApp.contains(".plus("), "Call site should be 'plus'");
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void missing_file_returns_exit_code_1() throws Exception {
        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "rename",
                "--file", "/nonexistent/Foo.java",
                "--line", "1", "--column", "1",
                "--name", "bar");

        assertEquals(1, exit);
    }

    // -------------------------------------------------------------------------
    // toOffset unit tests
    // -------------------------------------------------------------------------

    @Test
    void toOffset_first_line() {
        assertEquals(4, RenameCommand.toOffset("abcde", 1, 5));
    }

    @Test
    void toOffset_second_line() {
        // "abcd\nefgh": line 2 starts at index 5; col 2 → index 6 ('f')
        assertEquals(6, RenameCommand.toOffset("abcd\nefgh", 2, 2));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static CommandLine cli(StringWriter out) {
        CommandLine cmd = new CommandLine(new Main());
        cmd.setOut(new PrintWriter(out, true));
        cmd.setErr(new PrintWriter(out, true));
        cmd.setExecutionExceptionHandler((ex, c, pr) -> {
            c.getErr().println("Error: " + ex.getMessage());
            return 1;
        });
        return cmd;
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
