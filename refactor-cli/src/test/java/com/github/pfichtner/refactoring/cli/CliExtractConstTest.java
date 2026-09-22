package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code extract-const} CLI subcommand.
 */
@CliFixture(root = "fixtures/extract-const/simple/input/Foo.java")
class CliExtractConstTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {

        int exit = bed.cli().execute(
                "extract-const",
                "--file", bed.root().toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "23",
                "--name", "PI",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        bed.assertUnchanged();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_constant(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "extract-const",
                "--file", bed.root().toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "23",
                "--name", "PI");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(bed.root())).as("File should declare the new constant")
                .contains("private static final double PI = 3.14159;");
    }
}
