package com.github.pfichtner.cli;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@code extract-var} CLI subcommand.
 */
class CliExtractVarTest {

    private static final Path FIXTURE_FILE;

    static {
        try {
            FIXTURE_FILE = Path.of(
                    CliExtractVarTest.class.getClassLoader()
                            .getResource("fixtures/extract-var/simple/input/Foo.java")
                            .toURI()
            );
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);
        String before = java.nio.file.Files.readString(source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "extract-var",
                "--file", source.toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "21",
                "--name", "answer",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_variable(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "extract-var",
                "--file", source.toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "21",
                "--name", "answer");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("File should declare the new variable")
                .contains("int answer = 6 * 7;");
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
}