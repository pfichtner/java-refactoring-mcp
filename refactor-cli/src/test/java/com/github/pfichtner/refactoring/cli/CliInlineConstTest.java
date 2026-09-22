package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code inline-const} CLI subcommand.
 */
@CliFixture(root = "fixtures/inline-constant/int-constant/input/Foo.java")
class CliInlineConstTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {

        int exit = bed.cli().execute(
                "inline-const",
                "--file", bed.root().toString(),
                "--line", "5", "--column", "21",
                "--all-occurrences",
                "--remove-declaration",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        bed.assertUnchanged();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_inlined_constant(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "inline-const",
                "--file", bed.root().toString(),
                "--line", "5", "--column", "21",
                "--all-occurrences",
                "--remove-declaration");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        String result = Files.readString(bed.root());
        assertThat(result).as("Declaration must be removed").doesNotContain("MAX");
        assertThat(result).as("All uses must be replaced with the literal").contains("x <= 100");
    }
}
