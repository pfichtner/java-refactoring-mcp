package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code convert-anonymous} CLI subcommand.
 */
class CliConvertAnonymousTest {

    private static final Path FIXTURE_FILE;

    static {
        try {
            FIXTURE_FILE = Path.of(
                    CliConvertAnonymousTest.class.getClassLoader()
                            .getResource("fixtures/convert-anonymous/simple/input/Outer.java")
                            .toURI()
            );
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Outer.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);
        String before = java.nio.file.Files.readString(source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "convert-anonymous",
                "--file", source.toString(),
                "--line", "4", "--column", "22",
                "--name", "Worker",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_named_class(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Outer.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "convert-anonymous",
                "--file", source.toString(),
                "--line", "4", "--column", "22",
                "--name", "Worker");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        String result = java.nio.file.Files.readString(source);
        assertThat(result).as("Anonymous class should be replaced by a named class").contains("new Worker()");
        assertThat(result).as("Named class declaration should be added").contains("private class Worker");
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