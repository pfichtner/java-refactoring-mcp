package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code change-method-signature} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/change-method-signature/pom.xml")
class CliChangeMethodSignatureTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path converterFile = root.resolve("src/main/java/com/example/Converter.java");
        String before = Files.readString(converterFile);

        int exit = cli.execute(
                "change-method-signature",
                "--file", converterFile.toString(),
                "--method", "convert",
                "--param-order", "1,0",
                "--return-type", "Object",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(converterFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_new_signature_and_call_sites(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path converterFile = tmp.resolve("src/main/java/com/example/Converter.java");
        Path appFile = tmp.resolve("src/main/java/com/example/App.java");

        int exit = cli.execute(
                "change-method-signature",
                "--file", converterFile.toString(),
                "--method", "convert",
                "--param-order", "1,0",
                "--return-type", "Object");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(converterFile)).as("Declaration should use the new return type")
                .contains("Object convert(String prefix, int value)");
        assertThat(Files.readString(appFile)).as("Call sites should be reordered")
                .contains(".convert(\"val:\", 42)");
    }
}
