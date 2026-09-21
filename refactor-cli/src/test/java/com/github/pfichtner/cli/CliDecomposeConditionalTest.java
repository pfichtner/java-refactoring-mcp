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
 * Integration tests for the {@code decompose-conditional} CLI subcommand.
 */
class CliDecomposeConditionalTest {

    private static final Path FIXTURE_FILE;

    static {
        try {
            FIXTURE_FILE = Path.of(
                    CliDecomposeConditionalTest.class.getClassLoader()
                            .getResource("fixtures/decompose-conditional/basic/input/Foo.java")
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
                "decompose-conditional",
                "--file", source.toString(),
                "--line", "6", "--column", "9",
                "--name", "isAdultPremium",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_boolean_method(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "decompose-conditional",
                "--file", source.toString(),
                "--line", "6", "--column", "9",
                "--name", "isAdultPremium");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        String result = java.nio.file.Files.readString(source);
        assertThat(result).as("Conditional should be replaced by the method call")
                .contains("if (isAdultPremium())");
        assertThat(result).as("Boolean method should be declared")
                .contains("private boolean isAdultPremium()");
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