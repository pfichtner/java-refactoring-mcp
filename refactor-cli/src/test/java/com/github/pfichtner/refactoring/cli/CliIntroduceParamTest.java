package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code introduce-param} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/introduce-param/pom.xml")
class CliIntroduceParamTest {

    @Test
    void apply_writes_new_parameter(CliTestBed bed) throws Exception {
        Path greeterFile = bed.root().resolve("src/main/java/com/example/Greeter.java");

        int exit = bed.cli().execute(
                "introduce-param",
                "--file", greeterFile.toString(),
                "--start-line", "5", "--start-column", "40",
                "--end-line", "5", "--end-column", "47",
                "--name", "whom");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(greeterFile)).as("Method should declare the new parameter")
                .contains("public void greet(java.lang.String whom)");
    }
}
