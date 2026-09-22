package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code promote-to-field} CLI subcommand.
 */
@CliFixture(root = "fixtures/promote-to-field/without-initializer/input/Counter.java")
class CliPromoteToFieldTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path source = bed.root();
        String before = Files.readString(source);

        int exit = bed.cli().execute(
                "promote-to-field",
                "--file", source.toString(),
                "--line", "4", "--column", "15",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(source)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_field(CliTestBed bed) throws Exception {
        Path source = bed.root();

        int exit = bed.cli().execute(
                "promote-to-field",
                "--file", source.toString(),
                "--line", "4", "--column", "15");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        String result = Files.readString(source);
        assertThat(result).as("Local variable should become a field").contains("private int count;");
        assertThat(result).as("Method body should reference the field without a local declaration")
                .doesNotContain("\n        int count;");
    }
}
