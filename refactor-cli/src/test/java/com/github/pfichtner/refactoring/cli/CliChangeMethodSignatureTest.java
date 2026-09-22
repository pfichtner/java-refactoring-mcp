package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code change-method-signature} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/change-method-signature/pom.xml")
class CliChangeMethodSignatureTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path converterFile = bed.root().resolve("src/main/java/com/example/Converter.java");

        int exit = bed.cli().execute(
                "change-method-signature",
                "--file", converterFile.toString(),
                "--method", "convert",
                "--param-order", "1,0",
                "--return-type", "Object",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(bed.changedFiles()).isEmpty();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_new_signature_and_call_sites(CliTestBed bed) throws Exception {
        Path converterFile = bed.root().resolve("src/main/java/com/example/Converter.java");
        Path appFile = bed.root().resolve("src/main/java/com/example/App.java");

        int exit = bed.cli().execute(
                "change-method-signature",
                "--file", converterFile.toString(),
                "--method", "convert",
                "--param-order", "1,0",
                "--return-type", "Object");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(converterFile)).as("Declaration should use the new return type")
                .contains("Object convert(String prefix, int value)");
        assertThat(Files.readString(appFile)).as("Call sites should be reordered")
                .contains(".convert(\"val:\", 42)");
    }
}
