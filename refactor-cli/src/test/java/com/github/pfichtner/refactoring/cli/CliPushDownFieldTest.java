package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code push-down-field} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/push-down-field/pom.xml")
class CliPushDownFieldTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path vehicleFile = bed.root().resolve("src/main/java/com/example/Vehicle.java");

        int exit = bed.cli().execute(
                "push-down-field",
                "--file", vehicleFile.toString(),
                "--field", "maxSpeed",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        bed.assertUnchanged();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_moves_field_to_subclasses(CliTestBed bed) throws Exception {
        Path vehicleFile = bed.root().resolve("src/main/java/com/example/Vehicle.java");
        Path carFile = bed.root().resolve("src/main/java/com/example/Car.java");

        int exit = bed.cli().execute(
                "push-down-field",
                "--file", vehicleFile.toString(),
                "--field", "maxSpeed");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(vehicleFile)).as("Superclass should lose the field").doesNotContain("maxSpeed");
        assertThat(Files.readString(carFile)).as("Subclass should gain the field").contains("int maxSpeed");
    }
}
