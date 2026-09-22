package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@code pull-up-field} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/pull-up-field/pom.xml")
class CliPullUpFieldTest {

    @Test
    void apply_moves_field_to_superclass(CliTestBed bed) throws Exception {
        Path dogFile = bed.root().resolve("src/main/java/com/example/Dog.java");
        Path animalFile = bed.root().resolve("src/main/java/com/example/Animal.java");

        int exit = bed.cli().execute(
                "pull-up-field",
                "--file", dogFile.toString(),
                "--field", "breed");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(animalFile)).as("Superclass should gain the field").contains("String breed");
        assertThat(Files.readString(dogFile)).as("Subclass should lose the field").doesNotContain("String breed");
    }
}
