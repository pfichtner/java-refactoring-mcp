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
 * Integration tests for the {@code introduce-param} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/introduce-param/pom.xml")
class CliIntroduceParamTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path greeterFile = root.resolve("src/main/java/com/example/Greeter.java");
        String before = Files.readString(greeterFile);

        int exit = cli.execute(
                "introduce-param",
                "--file", greeterFile.toString(),
                "--start-line", "5", "--start-column", "40",
                "--end-line", "5", "--end-column", "47",
                "--name", "whom",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(greeterFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_new_parameter(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path greeterFile = tmp.resolve("src/main/java/com/example/Greeter.java");

        int exit = cli.execute(
                "introduce-param",
                "--file", greeterFile.toString(),
                "--start-line", "5", "--start-column", "40",
                "--end-line", "5", "--end-column", "47",
                "--name", "whom");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(greeterFile)).as("Method should declare the new parameter")
                .contains("public void greet(java.lang.String whom)");
    }
}
