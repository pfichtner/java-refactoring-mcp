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
 * Integration tests for the {@code convert-to-record} CLI subcommand.
 */
class CliConvertToRecordTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliConvertToRecordTest.class.getClassLoader()
                            .getResource("fixtures/projects/convert-to-record/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path pointFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Point.java");
        String before = Files.readString(pointFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "convert-to-record",
                "--file", pointFile.toString(),
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(pointFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_changed_files(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path pointFile = tmp.resolve("src/main/java/com/example/Point.java");
        Path appFile   = tmp.resolve("src/main/java/com/example/App.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "convert-to-record",
                "--file", pointFile.toString());

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(pointFile)).as("Point.java should become a record").contains("record Point");
        assertThat(Files.readString(appFile)).as("App.java getter call sites should be renamed").contains(".x()");
    }

    @Test
    void rejects_class_with_extends_and_returns_error() throws Exception {
        Path derivedFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Derived.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "convert-to-record",
                "--file", derivedFile.toString());

        assertThat(exit).as("Expected non-zero exit for rejected class").isNotEqualTo(0);
        assertThat(out.toString()).as("Error message should mention extends").contains("extends");
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
