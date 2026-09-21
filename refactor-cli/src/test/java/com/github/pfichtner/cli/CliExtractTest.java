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
 * Integration tests for the {@code extract} CLI subcommand.
 */
class CliExtractTest {

    private static final Path FIXTURE_FILE;

    static {
        try {
            FIXTURE_FILE = Path.of(
                    CliExtractTest.class.getClassLoader()
                            .getResource("fixtures/extract/simple/input/Greeter.java")
                            .toURI()
            );
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Greeter.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);
        String before = java.nio.file.Files.readString(source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "extract",
                "--file", source.toString(),
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_extracted_method(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Greeter.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "extract",
                "--file", source.toString(),
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(out.toString()).as("Apply output should mention the method name").contains("sayHi");
        assertThat(java.nio.file.Files.readString(source)).as("File should contain extracted method")
                .contains("private void sayHi()");
    }

    @Test
    void missing_file_returns_exit_code_1() throws Exception {
        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "extract",
                "--file", "/nonexistent/Greeter.java",
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi");

        assertThat(exit).isEqualTo(1);
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