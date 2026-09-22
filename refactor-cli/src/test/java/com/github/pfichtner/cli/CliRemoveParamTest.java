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
 * Integration tests for the {@code remove-param} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/remove-param/pom.xml")
class CliRemoveParamTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path computationFile = root.resolve("src/main/java/com/example/Computation.java");
        String before = Files.readString(computationFile);

        int exit = cli.execute(
                "remove-param",
                "--file", computationFile.toString(),
                "--method", "add",
                "--parameter", "c",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(computationFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_removed_parameter(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path computationFile = tmp.resolve("src/main/java/com/example/Computation.java");
        Path appFile = tmp.resolve("src/main/java/com/example/App.java");

        int exit = cli.execute(
                "remove-param",
                "--file", computationFile.toString(),
                "--method", "add",
                "--parameter", "c");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(computationFile)).as("Declaration should lose the parameter")
                .contains("public int add(int a, int b)");
        assertThat(Files.readString(appFile)).as("Call site should drop the argument")
                .contains("calc.add(1, 2)");
    }
}
