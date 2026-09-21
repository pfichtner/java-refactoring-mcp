package com.github.pfichtner.cli;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@code extract-superclass} CLI subcommand.
 */
class CliExtractSuperclassTest {

    private static final Path FIXTURE_FILE;

    static {
        try {
            FIXTURE_FILE = Path.of(
                    CliExtractSuperclassTest.class.getClassLoader()
                            .getResource("fixtures/extract-superclass/simple/input/Animal.java")
                            .toURI()
            );
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Animal.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);
        String before = java.nio.file.Files.readString(source);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "extract-superclass",
                "--file", source.toString(),
                "--name", "BaseAnimal",
                "--superclass-file", tmp.resolve("BaseAnimal.java").toString(),
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_superclass_file(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Animal.java");
        java.nio.file.Files.copy(FIXTURE_FILE, source);

        Path superclassFile = tmp.resolve("BaseAnimal.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "extract-superclass",
                "--file", source.toString(),
                "--name", "BaseAnimal",
                "--superclass-file", superclassFile.toString());

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(source)).as("Class should extend the superclass")
                .contains("extends BaseAnimal");
        assertThat(java.nio.file.Files.exists(superclassFile)).as("Superclass file should be written").isTrue();
        assertThat(java.nio.file.Files.readString(superclassFile))
                .as("Superclass should declare the extracted methods")
                .contains("public void breathe()");
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