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
 * Integration tests for name-based locator options in the CLI layer.
 *
 * Exercises {@code --method}, {@code --field}, {@code --type} as alternatives
 * to {@code --line}/{@code --column} on the {@code rename} command, and
 * verifies that mutually exclusive error cases produce non-zero exit codes.
 */
class CliLocatorByNameTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliLocatorByNameTest.class.getClassLoader()
                            .getResource("fixtures/projects/rename-method/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // -------------------------------------------------------------------------
    // rename --method (dry-run)
    // -------------------------------------------------------------------------

    @Test
    void dry_run_by_method_name_prints_preview() throws Exception {
        Path calcFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Calculator.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "rename",
                "--file", calcFile.toString(),
                "--method", "add",
                "--name", "plus",
                "--dry-run");

        assertEquals(0, exit, "Expected exit code 0: " + out);
        assertTrue(Files.readString(calcFile).contains("add"),
                "Dry-run must not modify the file on disk");

        Approvals.verify(out.toString());
    }

    // -------------------------------------------------------------------------
    // rename --method (apply, writes to temp copy)
    // -------------------------------------------------------------------------

    @Test
    void apply_by_method_name_writes_changed_files(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path calcFile = tmp.resolve("src/main/java/com/example/Calculator.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "rename",
                "--file", calcFile.toString(),
                "--method", "add",
                "--name", "plus");

        assertEquals(0, exit, "Expected exit code 0: " + out);

        String calc = Files.readString(calcFile);
        assertFalse(calc.contains(" add("), "Declaration should be renamed");
        assertTrue(calc.contains(" plus("), "Declaration should be 'plus'");

        String app = Files.readString(tmp.resolve("src/main/java/com/example/App.java"));
        assertTrue(app.contains(".plus("), "Call site should be updated");
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void position_and_method_together_causes_error() throws Exception {
        Path calcFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Calculator.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "rename",
                "--file", calcFile.toString(),
                "--line", "4", "--column", "16",
                "--method", "add",
                "--name", "plus",
                "--dry-run");

        assertNotEquals(0, exit, "Expected non-zero exit when both locators given");
    }

    @Test
    void no_locator_causes_error() throws Exception {
        Path calcFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Calculator.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "rename",
                "--file", calcFile.toString(),
                "--name", "plus",
                "--dry-run");

        assertNotEquals(0, exit, "Expected non-zero exit when no locator given");
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
