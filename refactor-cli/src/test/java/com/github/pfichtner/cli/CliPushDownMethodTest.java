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
 * Integration tests for the {@code push-down} CLI subcommand (method variant).
 */
@CliTestBed(root = "fixtures/projects/push-down-method/pom.xml")
class CliPushDownMethodTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path shapeFile = root.resolve("src/main/java/com/example/Shape.java");
        String before = Files.readString(shapeFile);

        int exit = cli.execute(
                "push-down",
                "--file", shapeFile.toString(),
                "--method", "area",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(shapeFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_moves_method_to_subclasses(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path shapeFile = tmp.resolve("src/main/java/com/example/Shape.java");
        Path circleFile = tmp.resolve("src/main/java/com/example/Circle.java");

        int exit = cli.execute(
                "push-down",
                "--file", shapeFile.toString(),
                "--method", "area");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(shapeFile)).as("Superclass should lose the method").doesNotContain("double area()");
        assertThat(Files.readString(circleFile)).as("Subclass should gain the method").contains("double area()");
    }
}
