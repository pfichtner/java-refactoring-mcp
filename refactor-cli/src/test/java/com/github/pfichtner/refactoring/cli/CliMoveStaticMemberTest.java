package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code move-static} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/move-static/pom.xml")
class CliMoveStaticMemberTest {

    @Test
    void apply_moves_static_member(CliTestBed bed) throws Exception {
        Path mathUtilsFile = bed.root().resolve("src/main/java/com/example/MathUtils.java");
        Path helpersFile = bed.root().resolve("src/main/java/com/example/Helpers.java");

        int exit = bed.cli().execute(
                "move-static",
                "--file", mathUtilsFile.toString(),
                "--line", "5", "--column", "5",
                "--target", "com.example.Helpers");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(helpersFile)).as("Target class should gain the static member")
                .contains("public static int square");
        assertThat(Files.readString(mathUtilsFile)).as("Source class should lose the member")
                .doesNotContain("square");
    }
}
