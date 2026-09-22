package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code move-class} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/move-class/pom.xml")
class CliMoveClassTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path calculatorFile = bed.root().resolve("src/main/java/com/example/service/Calculator.java");

        int exit = bed.cli().execute(
                "move-class",
                "--file", calculatorFile.toString(),
                "--package", "com.example.util",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(bed.changedFiles()).isEmpty();

        Approvals.verify(bed.out().toString(), new Options().withScrubber(scrubRoot(bed)));
    }

    private static Scrubber scrubRoot(CliTestBed bed) {
        String root = bed.root().toString() + java.io.File.separator;
        return input -> input.replace(root, "{ROOT}/");
    }

    @Test
    void apply_moves_class_and_updates_import(CliTestBed bed) throws Exception {
        Path calculatorFile = bed.root().resolve("src/main/java/com/example/service/Calculator.java");
        Path movedFile = bed.root().resolve("src/main/java/com/example/util/Calculator.java");
        Path appFile = bed.root().resolve("src/main/java/com/example/app/App.java");

        int exit = bed.cli().execute(
                "move-class",
                "--file", calculatorFile.toString(),
                "--package", "com.example.util");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.exists(movedFile)).as("Class should move to the new package directory").isTrue();
        assertThat(Files.notExists(calculatorFile)).as("Old location should be removed").isTrue();
        assertThat(Files.readString(appFile)).as("Import should be updated")
                .contains("import com.example.util.Calculator;");
    }
}
