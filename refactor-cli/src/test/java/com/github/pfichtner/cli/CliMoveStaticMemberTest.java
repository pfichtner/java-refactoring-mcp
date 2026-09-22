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
 * Integration tests for the {@code move-static} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/move-static/pom.xml")
class CliMoveStaticMemberTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path mathUtilsFile = root.resolve("src/main/java/com/example/MathUtils.java");
        String before = Files.readString(mathUtilsFile);

        int exit = cli.execute(
                "move-static",
                "--file", mathUtilsFile.toString(),
                "--line", "5", "--column", "5",
                "--target", "com.example.Helpers",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(mathUtilsFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_moves_static_member(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path mathUtilsFile = tmp.resolve("src/main/java/com/example/MathUtils.java");
        Path helpersFile = tmp.resolve("src/main/java/com/example/Helpers.java");

        int exit = cli.execute(
                "move-static",
                "--file", mathUtilsFile.toString(),
                "--line", "5", "--column", "5",
                "--target", "com.example.Helpers");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(helpersFile)).as("Target class should gain the static member")
                .contains("public static int square");
        assertThat(Files.readString(mathUtilsFile)).as("Source class should lose the member")
                .doesNotContain("square");
    }
}
