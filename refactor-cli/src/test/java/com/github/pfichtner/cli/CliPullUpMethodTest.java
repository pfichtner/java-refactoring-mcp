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
 * Integration tests for the {@code pull-up} CLI subcommand (method variant).
 */
@CliTestBed(root = "fixtures/projects/pull-up-method/pom.xml")
class CliPullUpMethodTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path dogFile = root.resolve("src/main/java/com/example/Dog.java");
        String before = Files.readString(dogFile);

        int exit = cli.execute(
                "pull-up",
                "--file", dogFile.toString(),
                "--method", "speak",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(dogFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_moves_method_to_superclass(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path dogFile = tmp.resolve("src/main/java/com/example/Dog.java");
        Path animalFile = tmp.resolve("src/main/java/com/example/Animal.java");

        int exit = cli.execute(
                "pull-up",
                "--file", dogFile.toString(),
                "--method", "speak");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(animalFile)).as("Superclass should gain the method").contains("void speak()");
        assertThat(Files.readString(dogFile)).as("Subclass should lose the method").doesNotContain("void speak()");
    }
}
