package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code extract-const} CLI subcommand.
 */
@CliTestBed(root = "fixtures/extract-const/simple/input/Foo.java")
class CliExtractConstTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        java.nio.file.Files.copy(root, source);
        String before = java.nio.file.Files.readString(source);

        int exit = cli.execute(
                "extract-const",
                "--file", source.toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "23",
                "--name", "PI",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_constant(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        java.nio.file.Files.copy(root, source);

        int exit = cli.execute(
                "extract-const",
                "--file", source.toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "23",
                "--name", "PI");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("File should declare the new constant")
                .contains("private static final double PI = 3.14159;");
    }
}
