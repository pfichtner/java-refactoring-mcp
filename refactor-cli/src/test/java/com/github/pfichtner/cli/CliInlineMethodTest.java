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
 * Integration tests for the {@code inline-method} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/rename-method/pom.xml")
class CliInlineMethodTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path appFile = root.resolve("src/main/java/com/example/App.java");
        String before = Files.readString(appFile);

        int exit = cli.execute(
                "inline-method",
                "--file", appFile.toString(),
                "--line", "6", "--column", "27",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(appFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_inlined_call(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path appFile = tmp.resolve("src/main/java/com/example/App.java");
        Path calcFile = tmp.resolve("src/main/java/com/example/Calculator.java");

        int exit = cli.execute(
                "inline-method",
                "--file", appFile.toString(),
                "--line", "6", "--column", "27",
                "--remove-declaration");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(appFile)).as("Call site should be replaced by the body").contains("int result = 1 + 2;");
        assertThat(Files.readString(calcFile)).as("Declaration should be removed").doesNotContain("public int add");
    }
}
