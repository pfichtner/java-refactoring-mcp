package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code decompose-conditional} CLI subcommand.
 */
@CliFixture(root = "fixtures/decompose-conditional/basic/input/Foo.java")
class CliDecomposeConditionalTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {

        int exit = bed.cli().execute(
                "decompose-conditional",
                "--file", bed.root().toString(),
                "--line", "6", "--column", "9",
                "--name", "isAdultPremium",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(bed.changedFiles()).isEmpty();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_boolean_method(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "decompose-conditional",
                "--file", bed.root().toString(),
                "--line", "6", "--column", "9",
                "--name", "isAdultPremium");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        String result = Files.readString(bed.root());
        assertThat(result).as("Conditional should be replaced by the method call")
                .contains("if (isAdultPremium())");
        assertThat(result).as("Boolean method should be declared")
                .contains("private boolean isAdultPremium()");
    }
}
