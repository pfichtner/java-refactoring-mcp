package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests {@code pull-up} CLI subcommand (method variant).
 */
@CliFixture(root = "fixtures/projects/pull-up-method/pom.xml")
class CliPullUpMethodTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path dogFile = bed.root().resolve("src/main/java/com/example/Dog.java");
        String before = Files.readString(dogFile);

        int exit = bed.cli().execute(
                "pull-up",
                "--file", dogFile.toString(),
                "--method", "speak",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(dogFile)).as("Dry-run not modify file on disk").isEqualTo(before);

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_moves_method_to_superclass(CliTestBed bed) throws Exception {
        Path dogFile = bed.root().resolve("src/main/java/com/example/Dog.java");
        Path animalFile = bed.root().resolve("src/main/java/com/example/Animal.java");

        int exit = bed.cli().execute(
                "pull-up",
                "--file", dogFile.toString(),
                "--method", "speak");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(animalFile)).as("Superclass should gain the method").contains("void speak()");
        assertThat(Files.readString(dogFile)).as("Subclass should lose the method").doesNotContain("void speak()");
    }
}
