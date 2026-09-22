package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code extract-superclass} CLI subcommand.
 */
@CliFixture(root = "fixtures/extract-superclass/simple/input/Animal.java")
class CliExtractSuperclassTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        String before = Files.readString(bed.root());

        int exit = bed.cli().execute(
                "extract-superclass",
                "--file", bed.root().toString(),
                "--name", "BaseAnimal",
                "--superclass-file", bed.root().getParent().resolve("BaseAnimal.java").toString(),
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(bed.root())).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_superclass_file(CliTestBed bed) throws Exception {
        Path superclassFile = bed.root().getParent().resolve("BaseAnimal.java");

        int exit = bed.cli().execute(
                "extract-superclass",
                "--file", bed.root().toString(),
                "--name", "BaseAnimal",
                "--superclass-file", superclassFile.toString());

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(bed.root())).as("Class should extend the superclass")
                .contains("extends BaseAnimal");
        assertThat(Files.exists(superclassFile)).as("Superclass file should be written").isTrue();
        assertThat(Files.readString(superclassFile))
                .as("Superclass should declare the extracted methods")
                .contains("public void breathe()");
    }
}
