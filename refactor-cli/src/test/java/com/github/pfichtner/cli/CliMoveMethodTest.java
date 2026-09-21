package com.github.pfichtner.cli;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@code move-method} CLI subcommand.
 */
class CliMoveMethodTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliMoveMethodTest.class.getClassLoader()
                            .getResource("fixtures/projects/move-method/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path printerFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Printer.java");
        String before = Files.readString(printerFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "move-method",
                "--file", printerFile.toString(),
                "--method", "format",
                "--target-class", "Report",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(printerFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_moves_method_to_target_class(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path printerFile = tmp.resolve("src/main/java/com/example/Printer.java");
        Path reportFile = tmp.resolve("src/main/java/com/example/Report.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "move-method",
                "--file", printerFile.toString(),
                "--method", "format",
                "--target-class", "Report");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(reportFile)).as("Target class should gain the method")
                .contains("String format");
        assertThat(Files.readString(printerFile)).as("Source class should lose the method")
                .doesNotContain("String format");
    }

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