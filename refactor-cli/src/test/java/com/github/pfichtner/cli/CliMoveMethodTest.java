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
 * Integration tests for the {@code move-method} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/move-method/pom.xml")
class CliMoveMethodTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path printerFile = root.resolve("src/main/java/com/example/Printer.java");
        String before = Files.readString(printerFile);

        int exit = cli.execute(
                "move-method",
                "--file", printerFile.toString(),
                "--method", "format",
                "--target-class", "com.example.Report",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(printerFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_moves_method_to_target_class(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path printerFile = tmp.resolve("src/main/java/com/example/Printer.java");
        Path reportFile = tmp.resolve("src/main/java/com/example/Report.java");

        int exit = cli.execute(
                "move-method",
                "--file", printerFile.toString(),
                "--method", "format",
                "--target-class", "com.example.Report");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(reportFile)).as("Target class should gain the method")
                .contains("String format");
        assertThat(Files.readString(printerFile)).as("Source class should lose the method")
                .doesNotContain("String format");
    }
}
