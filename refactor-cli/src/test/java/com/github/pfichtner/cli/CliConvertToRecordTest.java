package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code convert-to-record} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/convert-to-record/pom.xml")
class CliConvertToRecordTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path pointFile = bed.root().resolve("src/main/java/com/example/Point.java");
        String before = Files.readString(pointFile);

        int exit = bed.cli().execute(
                "convert-to-record",
                "--file", pointFile.toString(),
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(pointFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_changed_files(CliTestBed bed) throws Exception {
        Path pointFile = bed.root().resolve("src/main/java/com/example/Point.java");
        Path appFile   = bed.root().resolve("src/main/java/com/example/App.java");

        int exit = bed.cli().execute(
                "convert-to-record",
                "--file", pointFile.toString());

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(pointFile)).as("Point.java should become a record").contains("record Point");
        assertThat(Files.readString(appFile)).as("App.java getter call sites should be renamed").contains(".x()");
    }

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
