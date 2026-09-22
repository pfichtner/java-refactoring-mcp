package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code extract} CLI subcommand.
 */
@CliFixture(root = "fixtures/extract/simple/input/Greeter.java")
class CliExtractTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        String before = Files.readString(bed.root());

        int exit = bed.cli().execute(
                "extract",
                "--file", bed.root().toString(),
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(bed.root())).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_extracted_method(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "extract",
                "--file", bed.root().toString(),
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(bed.out().toString()).as("Apply output should mention the method name").contains("sayHi");
        assertThat(Files.readString(bed.root())).as("File should contain extracted method")
                .contains("private void sayHi()");
    }

    @Test
    void missing_file_returns_exit_code_1(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "extract",
                "--file", "/nonexistent/Greeter.java",
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi");

        assertThat(exit).isEqualTo(1);
    }
}
