package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code convert-anonymous} CLI subcommand.
 */
@CliTestBed(root = "fixtures/convert-anonymous/simple/input/Outer.java")
class CliConvertAnonymousTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Outer.java");
        java.nio.file.Files.copy(root, source);
        String before = java.nio.file.Files.readString(source);

        int exit = cli.execute(
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
    void apply_writes_named_class(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Outer.java");
        java.nio.file.Files.copy(root, source);

        int exit = cli.execute(
                "convert-anonymous",
                "--file", source.toString(),
                "--line", "4", "--column", "22",
                "--name", "Worker");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        String result = java.nio.file.Files.readString(source);
        assertThat(result).as("Anonymous class should be replaced by a named class").contains("new Worker()");
        assertThat(result).as("Named class declaration should be added").contains("private class Worker");
    }
}
