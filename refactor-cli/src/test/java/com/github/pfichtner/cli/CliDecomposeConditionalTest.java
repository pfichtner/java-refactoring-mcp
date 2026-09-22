package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code decompose-conditional} CLI subcommand.
 */
@CliTestBed(root = "fixtures/decompose-conditional/basic/input/Foo.java")
class CliDecomposeConditionalTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        java.nio.file.Files.copy(root, source);
        String before = java.nio.file.Files.readString(source);

        int exit = cli.execute(
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
    void apply_writes_boolean_method(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        java.nio.file.Files.copy(root, source);

        int exit = cli.execute(
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
}
