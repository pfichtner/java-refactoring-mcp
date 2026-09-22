package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code move-class} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/move-class/pom.xml")
class CliMoveClassTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path calculatorFile = root.resolve("src/main/java/com/example/service/Calculator.java");
        String before = Files.readString(calculatorFile);

        int exit = cli.execute(
                "move-class",
                "--file", calculatorFile.toString(),
                "--package", "com.example.util",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(calculatorFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString(), new Options().withScrubber(scrubProjectRoot()));
    }

    private static Scrubber scrubProjectRoot() {
        String projectRoot = Path.of("").toAbsolutePath().normalize().toString();
        return input -> input.replace(projectRoot + java.io.File.separator, "{ROOT}/");
    }

    @Test
    void apply_moves_class_and_updates_import(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path calculatorFile = tmp.resolve("src/main/java/com/example/service/Calculator.java");
        Path movedFile = tmp.resolve("src/main/java/com/example/util/Calculator.java");
        Path appFile = tmp.resolve("src/main/java/com/example/app/App.java");

        int exit = cli.execute(
                "move-class",
                "--file", calculatorFile.toString(),
                "--package", "com.example.util");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.exists(movedFile)).as("Class should move to the new package directory").isTrue();
        assertThat(Files.notExists(calculatorFile)).as("Old location should be removed").isTrue();
        assertThat(Files.readString(appFile)).as("Import should be updated")
                .contains("import com.example.util.Calculator;");
    }
}
