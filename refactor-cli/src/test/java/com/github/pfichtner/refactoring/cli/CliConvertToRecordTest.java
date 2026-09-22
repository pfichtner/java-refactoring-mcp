package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Error-case tests for the {@code convert-to-record} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/convert-to-record/pom.xml")
class CliConvertToRecordTest {

    @Test
    void rejects_class_with_extends_and_returns_error(CliTestBed bed) throws Exception {
        Path derivedFile = bed.root().resolve("src/main/java/com/example/Derived.java");

        int exit = bed.cli().execute(
                "convert-to-record",
                "--file", derivedFile.toString());

        assertThat(exit).as("Expected non-zero exit for rejected class").isNotEqualTo(0);
        assertThat(bed.out().toString()).as("Error message should mention extends").contains("extends");
    }
}
