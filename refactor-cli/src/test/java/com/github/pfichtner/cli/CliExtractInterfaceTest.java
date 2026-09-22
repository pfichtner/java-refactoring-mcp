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
 * Integration tests for the {@code extract-interface} CLI subcommand.
 */
@CliTestBed(root = "fixtures/extract-interface/simple/input/Calculator.java")
class CliExtractInterfaceTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Calculator.java");
        Files.copy(root, source);
        String before = Files.readString(source);

        int exit = cli.execute(
                "extract-interface",
                "--file", source.toString(),
                "--name", "Arithmetic",
                "--interface-file", tmp.resolve("Arithmetic.java").toString(),
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_interface_file(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Calculator.java");
        Files.copy(root, source);

        Path interfaceFile = tmp.resolve("Arithmetic.java");

        int exit = cli.execute(
                "extract-interface",
                "--file", source.toString(),
                "--name", "Arithmetic",
                "--interface-file", interfaceFile.toString());

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(source)).as("Class should implement the interface")
                .contains("implements Arithmetic");
        assertThat(Files.exists(interfaceFile)).as("Interface file should be written").isTrue();
        assertThat(Files.readString(interfaceFile))
                .as("Interface should declare the extracted methods")
                .contains("int add(int a, int b)");
    }
}
