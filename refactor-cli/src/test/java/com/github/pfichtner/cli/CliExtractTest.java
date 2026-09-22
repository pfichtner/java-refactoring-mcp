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
 * Integration tests for the {@code extract} CLI subcommand.
 */
@CliTestBed(root = "fixtures/extract/simple/input/Greeter.java")
class CliExtractTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Greeter.java");
        Files.copy(root, source);
        String before = Files.readString(source);

        int exit = cli.execute(
                "extract",
                "--file", source.toString(),
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_extracted_method(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Greeter.java");
        Files.copy(root, source);

        int exit = cli.execute(
                "extract",
                "--file", source.toString(),
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(out.toString()).as("Apply output should mention the method name").contains("sayHi");
        assertThat(Files.readString(source)).as("File should contain extracted method")
                .contains("private void sayHi()");
    }

    @Test
    void missing_file_returns_exit_code_1(CommandLine cli) throws Exception {
        int exit = cli.execute(
                "extract",
                "--file", "/nonexistent/Greeter.java",
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi");

        assertThat(exit).isEqualTo(1);
    }
}
