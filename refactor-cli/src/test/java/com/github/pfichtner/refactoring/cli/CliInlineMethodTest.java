package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code inline-method} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/rename-method/pom.xml")
class CliInlineMethodTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path appFile = bed.root().resolve("src/main/java/com/example/App.java");
        String before = Files.readString(appFile);

        int exit = bed.cli().execute(
                "inline-method",
                "--file", appFile.toString(),
                "--line", "6", "--column", "27",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(appFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_inlined_call(CliTestBed bed) throws Exception {
        Path appFile = bed.root().resolve("src/main/java/com/example/App.java");
        Path calcFile = bed.root().resolve("src/main/java/com/example/Calculator.java");

        int exit = bed.cli().execute(
                "inline-method",
                "--file", appFile.toString(),
                "--line", "6", "--column", "27",
                "--remove-declaration");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(appFile)).as("Call site should be replaced by the body").contains("int result = 1 + 2;");
        assertThat(Files.readString(calcFile)).as("Declaration should be removed").doesNotContain("public int add");
    }
}
