package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code encapsulate-field} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/encapsulate-field/pom.xml")
class CliEncapsulateFieldTest {

    @Test
    void dry_run_prints_getter_preview_without_writing(CliTestBed bed) throws Exception {
        Path personFile = bed.root().resolve("src/main/java/com/example/Person.java");
        String before = Files.readString(personFile);

        int exit = bed.cli().execute(
                "encapsulate-field",
                "--file", personFile.toString(),
                "--field", "name",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(personFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(bed.out().toString());
    }

    @Test
    void dry_run_prints_getter_and_setter_preview(CliTestBed bed) throws Exception {
        Path personFile = bed.root().resolve("src/main/java/com/example/Person.java");

        int exit = bed.cli().execute(
                "encapsulate-field",
                "--file", personFile.toString(),
                "--field", "name",
                "--setter",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(bed.out().toString()).as("Preview should show a setter").contains("setName");

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_getter_and_updates_call_sites(CliTestBed bed) throws Exception {
        Path personFile = bed.root().resolve("src/main/java/com/example/Person.java");
        Path appFile = bed.root().resolve("src/main/java/com/example/App.java");

        int exit = bed.cli().execute(
                "encapsulate-field",
                "--file", personFile.toString(),
                "--field", "name");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(personFile)).as("Field should become private with getter")
                .contains("private String name;")
                .contains("public String getName()");
        assertThat(Files.readString(appFile)).as("Read access should use the getter").contains("p.getName()");
    }
}
