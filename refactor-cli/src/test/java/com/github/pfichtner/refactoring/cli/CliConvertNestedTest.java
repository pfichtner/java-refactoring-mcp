package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code convert-nested} CLI subcommand.
 */
@CliFixture(root = "fixtures/convert-nested/static-class/input/Outer.java")
class CliConvertNestedTest {

    @Test
    void apply_writes_top_level_class(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "convert-nested",
                "--file", bed.root().toString(),
                "--line", "15", "--column", "25");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(bed.root())).as("Nested class should be removed")
                .doesNotContain("class Helper");
        assertThat(Files.exists(bed.root().getParent().resolve("Helper.java")))
                .as("New top-level class file should be written").isTrue();
    }
}
