package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code move-method} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/move-method/pom.xml")
class CliMoveMethodTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path printerFile = bed.root().resolve("src/main/java/com/example/Printer.java");

        int exit = bed.cli().execute(
                "move-method",
                "--file", printerFile.toString(),
                "--method", "format",
                "--target-class", "com.example.Report",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        bed.assertUnchanged();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_moves_method_to_target_class(CliTestBed bed) throws Exception {
        Path printerFile = bed.root().resolve("src/main/java/com/example/Printer.java");
        Path reportFile = bed.root().resolve("src/main/java/com/example/Report.java");

        int exit = bed.cli().execute(
                "move-method",
                "--file", printerFile.toString(),
                "--method", "format",
                "--target-class", "com.example.Report");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(reportFile)).as("Target class should gain the method")
                .contains("String format");
        assertThat(Files.readString(printerFile)).as("Source class should lose the method")
                .doesNotContain("String format");
    }
}
