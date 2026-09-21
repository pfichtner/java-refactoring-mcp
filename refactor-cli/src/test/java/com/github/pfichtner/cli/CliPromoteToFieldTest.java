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
 * Integration tests for the {@code promote-to-field} CLI subcommand.
 */
class CliPromoteToFieldTest {

    private static final Path FIXTURE_FILE;

    static {
        try {
            FIXTURE_FILE = Path.of(
                    CliPromoteToFieldTest.class.getClassLoader()
                            .getResource("fixtures/promote-to-field/without-initializer/input/Counter.java")
                            .toURI()
            );
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Counter.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);
        String before = java.nio.file.Files.readString(source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "promote-to-field",
                "--file", source.toString(),
                "--line", "4", "--column", "15",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_field(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Counter.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "promote-to-field",
                "--file", source.toString(),
                "--line", "4", "--column", "15");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        String result = java.nio.file.Files.readString(source);
        assertThat(result).as("Local variable should become a field").contains("private int count;");
        assertThat(result).as("Method body should reference the field without a local declaration")
                .doesNotContain("\n        int count;");
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