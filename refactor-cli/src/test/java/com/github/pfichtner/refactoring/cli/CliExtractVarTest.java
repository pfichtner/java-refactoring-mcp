package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code extract-var} CLI subcommand.
 */
@CliFixture(root = "fixtures/extract-var/simple/input/Foo.java")
class CliExtractVarTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {

        int exit = bed.cli().execute(
                "extract-var",
                "--file", bed.root().toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "21",
                "--name", "answer",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(bed.changedFiles()).isEmpty();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_variable(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "extract-var",
                "--file", bed.root().toString(),
                "--start-line", "3", "--start-column", "16",
                "--end-line", "3", "--end-column", "21",
                "--name", "answer");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(bed.root())).as("File should declare the new variable")
                .contains("int answer = 6 * 7;");
    }
}
