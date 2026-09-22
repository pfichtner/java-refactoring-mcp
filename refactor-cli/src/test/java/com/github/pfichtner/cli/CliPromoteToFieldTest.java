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
 * Integration tests for the {@code promote-to-field} CLI subcommand.
 */
@CliTestBed(root = "fixtures/promote-to-field/without-initializer/input/Counter.java")
class CliPromoteToFieldTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Counter.java");
        Files.copy(root, source);
        String before = Files.readString(source);

        int exit = cli.execute(
                "promote-to-field",
                "--file", source.toString(),
                "--line", "4", "--column", "15",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_field(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Counter.java");
        Files.copy(root, source);

        int exit = cli.execute(
                "promote-to-field",
                "--file", source.toString(),
                "--line", "4", "--column", "15");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        String result = Files.readString(source);
        assertThat(result).as("Local variable should become a field").contains("private int count;");
        assertThat(result).as("Method body should reference the field without a local declaration")
                .doesNotContain("\n        int count;");
    }
}
