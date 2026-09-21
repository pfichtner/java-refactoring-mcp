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
 * Integration tests for the {@code inline-const} CLI subcommand.
 */
class CliInlineConstTest {

    private static final Path FIXTURE_FILE;

    static {
        try {
            FIXTURE_FILE = Path.of(
                    CliInlineConstTest.class.getClassLoader()
                            .getResource("fixtures/inline-constant/int-constant/input/Foo.java")
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
                "inline-const",
                "--file", source.toString(),
                "--line", "5", "--column", "21",
                "--all-occurrences",
                "--remove-declaration",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_inlined_constant(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "inline-const",
                "--file", source.toString(),
                "--line", "5", "--column", "21",
                "--all-occurrences",
                "--remove-declaration");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        String result = java.nio.file.Files.readString(source);
        assertThat(result).as("Declaration must be removed").doesNotContain("MAX");
        assertThat(result).as("All uses must be replaced with the literal").contains("x <= 100");
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