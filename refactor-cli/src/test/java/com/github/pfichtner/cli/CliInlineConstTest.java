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
 * Integration tests for the {@code inline-const} CLI subcommand.
 */
@CliTestBed(root = "fixtures/inline-constant/int-constant/input/Foo.java")
class CliInlineConstTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        Files.copy(root, source);
        String before = Files.readString(source);

        int exit = cli.execute(
                "inline-const",
                "--file", source.toString(),
                "--line", "5", "--column", "21",
                "--all-occurrences",
                "--remove-declaration",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_inlined_constant(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Foo.java");
        Files.copy(root, source);

        int exit = cli.execute(
                "inline-const",
                "--file", source.toString(),
                "--line", "5", "--column", "21",
                "--all-occurrences",
                "--remove-declaration");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        String result = Files.readString(source);
        assertThat(result).as("Declaration must be removed").doesNotContain("MAX");
        assertThat(result).as("All uses must be replaced with the literal").contains("x <= 100");
    }
}
