package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code convert-anonymous} CLI subcommand.
 */
@CliFixture(root = "fixtures/convert-anonymous/simple/input/Outer.java")
class CliConvertAnonymousTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {

        int exit = bed.cli().execute(
                "convert-anonymous",
                "--file", bed.root().toString(),
                "--line", "4", "--column", "22",
                "--name", "Worker",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        bed.assertUnchanged();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_named_class(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "convert-anonymous",
                "--file", bed.root().toString(),
                "--line", "4", "--column", "22",
                "--name", "Worker");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        String result = java.nio.file.Files.readString(bed.root());
        assertThat(result).as("Anonymous class should be replaced by a named class").contains("new Worker()");
        assertThat(result).as("Named class declaration should be added").contains("private class Worker");
    }
}
