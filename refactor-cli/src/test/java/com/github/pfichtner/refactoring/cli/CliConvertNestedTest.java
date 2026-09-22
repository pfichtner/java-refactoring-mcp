package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code convert-nested} CLI subcommand.
 */
@CliFixture(root = "fixtures/convert-nested/static-class/input/Outer.java")
class CliConvertNestedTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        String before = java.nio.file.Files.readString(bed.root());

        int exit = bed.cli().execute(
                "convert-nested",
                "--file", bed.root().toString(),
                "--line", "15", "--column", "25",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(bed.root())).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_top_level_class(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "convert-nested",
                "--file", bed.root().toString(),
                "--line", "15", "--column", "25");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(java.nio.file.Files.readString(bed.root())).as("Nested class should be removed")
                .doesNotContain("class Helper");
        assertThat(java.nio.file.Files.exists(bed.root().getParent().resolve("Helper.java")))
                .as("New top-level class file should be written").isTrue();
    }
}
