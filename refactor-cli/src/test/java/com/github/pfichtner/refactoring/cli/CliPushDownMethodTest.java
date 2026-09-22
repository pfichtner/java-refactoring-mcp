package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code push-down} CLI subcommand (method variant).
 */
@CliFixture(root = "fixtures/projects/push-down-method/pom.xml")
class CliPushDownMethodTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path shapeFile = bed.root().resolve("src/main/java/com/example/Shape.java");

        int exit = bed.cli().execute(
                "push-down",
                "--file", shapeFile.toString(),
                "--method", "area",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        bed.assertUnchanged();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_moves_method_to_subclasses(CliTestBed bed) throws Exception {
        Path shapeFile = bed.root().resolve("src/main/java/com/example/Shape.java");
        Path circleFile = bed.root().resolve("src/main/java/com/example/Circle.java");

        int exit = bed.cli().execute(
                "push-down",
                "--file", shapeFile.toString(),
                "--method", "area");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(shapeFile)).as("Superclass should lose the method").doesNotContain("double area()");
        assertThat(Files.readString(circleFile)).as("Subclass should gain the method").contains("double area()");
    }
}
