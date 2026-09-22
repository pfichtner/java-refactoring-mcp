package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code remove-param} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/remove-param/pom.xml")
class CliRemoveParamTest {

    @Test
    void apply_writes_removed_parameter(CliTestBed bed) throws Exception {
        Path computationFile = bed.root().resolve("src/main/java/com/example/Computation.java");
        Path appFile = bed.root().resolve("src/main/java/com/example/App.java");

        int exit = bed.cli().execute(
                "remove-param",
                "--file", computationFile.toString(),
                "--method", "add",
                "--parameter", "c");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(computationFile)).as("Declaration should lose the parameter")
                .contains("public int add(int a, int b)");
        assertThat(Files.readString(appFile)).as("Call site should drop the argument")
                .contains("calc.add(1, 2)");
    }
}
