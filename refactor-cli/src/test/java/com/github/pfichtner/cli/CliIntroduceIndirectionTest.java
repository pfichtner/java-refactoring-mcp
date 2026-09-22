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
 * Integration tests for the {@code introduce-indirection} CLI subcommand.
 */
@CliTestBed(root = "fixtures/introduce-indirection/static-method/input/MathUtils.java")
class CliIntroduceIndirectionTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("MathUtils.java");
        Files.copy(root, source);
        String before = Files.readString(source);

        int exit = cli.execute(
                "introduce-indirection",
                "--file", source.toString(),
                "--line", "3", "--column", "26",
                "--name", "computeSquare",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_indirection_method(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("MathUtils.java");
        Files.copy(root, source);

        int exit = cli.execute(
                "introduce-indirection",
                "--file", source.toString(),
                "--line", "3", "--column", "26",
                "--name", "computeSquare");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        String result = Files.readString(source);
        assertThat(result).as("Original method should be unchanged").contains("public static int square(int x)");
        assertThat(result).as("Indirection method should delegate to the original")
                .contains("public static int computeSquare(int x)")
                .contains("return square(x);");
    }
}
