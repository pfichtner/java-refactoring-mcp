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
 * Integration tests for the {@code extract-superclass} CLI subcommand.
 */
@CliTestBed(root = "fixtures/extract-superclass/simple/input/Animal.java")
class CliExtractSuperclassTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Animal.java");
        Files.copy(root, source);
        String before = Files.readString(source);

        int exit = cli.execute(
                "extract-superclass",
                "--file", source.toString(),
                "--name", "BaseAnimal",
                "--superclass-file", tmp.resolve("BaseAnimal.java").toString(),
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_superclass_file(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Animal.java");
        Files.copy(root, source);

        Path superclassFile = tmp.resolve("BaseAnimal.java");

        int exit = cli.execute(
                "extract-superclass",
                "--file", source.toString(),
                "--name", "BaseAnimal",
                "--superclass-file", superclassFile.toString());

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(source)).as("Class should extend the superclass")
                .contains("extends BaseAnimal");
        assertThat(Files.exists(superclassFile)).as("Superclass file should be written").isTrue();
        assertThat(Files.readString(superclassFile))
                .as("Superclass should declare the extracted methods")
                .contains("public void breathe()");
    }
}
