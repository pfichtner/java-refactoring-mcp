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
 * Integration tests for the {@code push-down-field} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/push-down-field/pom.xml")
class CliPushDownFieldTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path vehicleFile = root.resolve("src/main/java/com/example/Vehicle.java");
        String before = Files.readString(vehicleFile);

        int exit = cli.execute(
                "push-down-field",
                "--file", vehicleFile.toString(),
                "--field", "maxSpeed",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(vehicleFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_moves_field_to_subclasses(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path vehicleFile = tmp.resolve("src/main/java/com/example/Vehicle.java");
        Path carFile = tmp.resolve("src/main/java/com/example/Car.java");

        int exit = cli.execute(
                "push-down-field",
                "--file", vehicleFile.toString(),
                "--field", "maxSpeed");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(vehicleFile)).as("Superclass should lose the field").doesNotContain("maxSpeed");
        assertThat(Files.readString(carFile)).as("Subclass should gain the field").contains("int maxSpeed");
    }
}
