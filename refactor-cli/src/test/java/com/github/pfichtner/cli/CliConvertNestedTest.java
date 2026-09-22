package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code convert-nested} CLI subcommand.
 */
@CliTestBed(root = "fixtures/convert-nested/static-class/input/Outer.java")
class CliConvertNestedTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Outer.java");
        java.nio.file.Files.copy(root, source);
        String before = java.nio.file.Files.readString(source);

        int exit = cli.execute(
                "convert-nested",
                "--file", source.toString(),
                "--line", "15", "--column", "25",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_top_level_class(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Outer.java");
        java.nio.file.Files.copy(root, source);

        int exit = cli.execute(
                "convert-nested",
                "--file", source.toString(),
                "--line", "15", "--column", "25");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Nested class should be removed")
                .doesNotContain("class Helper");
        assertThat(java.nio.file.Files.exists(tmp.resolve("Helper.java")))
                .as("New top-level class file should be written").isTrue();
    }
}
