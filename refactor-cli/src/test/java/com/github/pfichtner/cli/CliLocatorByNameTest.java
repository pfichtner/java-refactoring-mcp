package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for name-based locator options in the CLI layer.
 *
 * Exercises {@code --method}, {@code --field}, {@code --type} as alternatives
 * to {@code --line}/{@code --column} on the {@code rename} command, and
 * verifies that mutually exclusive error cases produce non-zero exit codes.
 */
@CliFixture(root = "fixtures/projects/rename-method/pom.xml")
class CliLocatorByNameTest {

    // -------------------------------------------------------------------------
    // rename --method (dry-run)
    // -------------------------------------------------------------------------

    @Test
    void dry_run_by_method_name_prints_preview(CliTestBed bed) throws Exception {
        Path calcFile = bed.root().resolve("src/main/java/com/example/Calculator.java");

        int exit = bed.cli().execute(
                "rename",
                "--file", calcFile.toString(),
                "--method", "add",
                "--name", "plus",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(calcFile)).as("Dry-run must not modify the file on disk").contains("add");

        Approvals.verify(bed.out().toString());
    }

    // -------------------------------------------------------------------------
    // rename --method (apply, writes to temp copy)
    // -------------------------------------------------------------------------

    @Test
    void apply_by_method_name_writes_changed_files(CliTestBed bed) throws Exception {
        Path calcFile = bed.root().resolve("src/main/java/com/example/Calculator.java");

        int exit = bed.cli().execute(
                "rename",
                "--file", calcFile.toString(),
                "--method", "add",
                "--name", "plus");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);

        String calc = Files.readString(calcFile);
        assertThat(calc).as("Declaration should be renamed").doesNotContain(" add(");
        assertThat(calc).as("Declaration should be 'plus'").contains(" plus(");

        String app = Files.readString(bed.root().resolve("src/main/java/com/example/App.java"));
        assertThat(app).as("Call site should be updated").contains(".plus(");
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void position_and_method_together_causes_error(CliTestBed bed) throws Exception {
        Path calcFile = bed.root().resolve("src/main/java/com/example/Calculator.java");

        int exit = bed.cli().execute(
                "rename",
                "--file", calcFile.toString(),
                "--line", "4", "--column", "16",
                "--method", "add",
                "--name", "plus",
                "--dry-run");

        assertThat(exit).as("Expected non-zero exit when both locators given").isNotEqualTo(0);
    }

    @Test
    void no_locator_causes_error(CliTestBed bed) throws Exception {
        Path calcFile = bed.root().resolve("src/main/java/com/example/Calculator.java");

        int exit = bed.cli().execute(
                "rename",
                "--file", calcFile.toString(),
                "--name", "plus",
                "--dry-run");

        assertThat(exit).as("Expected non-zero exit when no locator given").isNotEqualTo(0);
    }
}
