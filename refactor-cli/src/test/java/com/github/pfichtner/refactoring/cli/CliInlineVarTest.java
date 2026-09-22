package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code inline-var} CLI subcommand.
 */
@CliFixture(root = "fixtures/inline-var/multiple-uses/input/Foo.java")
class CliInlineVarTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {

        int exit = bed.cli().execute(
                "inline-var",
                "--file", bed.root().toString(),
                "--line", "3", "--column", "16",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(bed.changedFiles()).isEmpty();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_inlined_variable(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "inline-var",
                "--file", bed.root().toString(),
                "--line", "3", "--column", "16");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        String result = Files.readString(bed.root());
        assertThat(result).as("Declaration must be removed").doesNotContain("String msg");
        assertThat(result).as("Uses must be replaced with the literal").contains("println(\"Hello\")");
    }
}
