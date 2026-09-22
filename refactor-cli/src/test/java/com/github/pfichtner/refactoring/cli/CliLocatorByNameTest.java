package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Error-case tests for name-based locator options in the CLI layer.
 */
@CliFixture(root = "fixtures/projects/rename-method/pom.xml")
class CliLocatorByNameTest {

    @Test
    void position_and_method_together_causes_error(CliTestBed bed) throws Exception {
        Path calcFile = bed.root().resolve("src/main/java/com/example/Calculator.java");

        int exit = bed.cli().execute(
                "rename",
                "--file", calcFile.toString(),
                "--line", "4", "--column", "16",
                "--method", "add",
                "--name", "plus",
                "--dry-run");

        assertThat(exit).as("Expected non-zero exit when both locators given").isNotEqualTo(0);
    }

    @Test
    void no_locator_causes_error(CliTestBed bed) throws Exception {
        Path calcFile = bed.root().resolve("src/main/java/com/example/Calculator.java");

        int exit = bed.cli().execute(
                "rename",
                "--file", calcFile.toString(),
                "--name", "plus",
                "--dry-run");

        assertThat(exit).as("Expected non-zero exit when no locator given").isNotEqualTo(0);
    }
}
