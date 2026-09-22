package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code extract-var} CLI subcommand.
 */
@CliTestBed(root = "fixtures/extract-var/simple/input/Foo.java")
class CliExtractVarTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        Files.copy(root, source);
        String before = Files.readString(source);

        int exit = cli.execute(
                "extract-var",
                "--file", source.toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "21",
                "--name", "answer",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_variable(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        Files.copy(root, source);

        int exit = cli.execute(
                "extract-var",
                "--file", source.toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "21",
                "--name", "answer");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(source)).as("File should declare the new variable")
                .contains("int answer = 6 * 7;");
    }
}
