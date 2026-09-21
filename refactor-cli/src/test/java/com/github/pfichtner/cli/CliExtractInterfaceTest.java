package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code extract-interface} CLI subcommand.
 */
class CliExtractInterfaceTest {

    private static final Path FIXTURE_FILE;

    static {
        try {
            FIXTURE_FILE = Path.of(
                    CliExtractInterfaceTest.class.getClassLoader()
                            .getResource("fixtures/extract-interface/simple/input/Calculator.java")
                            .toURI()
            );
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Calculator.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);
        String before = java.nio.file.Files.readString(source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "extract-interface",
                "--file", source.toString(),
                "--name", "Arithmetic",
                "--interface-file", tmp.resolve("Arithmetic.java").toString(),
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_interface_file(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Calculator.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);

        Path interfaceFile = tmp.resolve("Arithmetic.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "extract-interface",
                "--file", source.toString(),
                "--name", "Arithmetic",
                "--interface-file", interfaceFile.toString());

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Class should implement the interface")
                .contains("implements Arithmetic");
        assertThat(java.nio.file.Files.exists(interfaceFile)).as("Interface file should be written").isTrue();
        assertThat(java.nio.file.Files.readString(interfaceFile))
                .as("Interface should declare the extracted methods")
                .contains("int add(int a, int b)");
    }

    private static CommandLine cli(StringWriter out) {
        CommandLine cmd = new CommandLine(new Main());
        cmd.setOut(new PrintWriter(out, true));
        cmd.setErr(new PrintWriter(out, true));
        cmd.setExecutionExceptionHandler((ex, c, pr) -> {
            c.getErr().println("Error: " + ex.getMessage());
            return 1;
        });
        return cmd;
    }
}