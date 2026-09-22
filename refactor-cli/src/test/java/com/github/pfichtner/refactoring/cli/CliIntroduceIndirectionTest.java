package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code introduce-indirection} CLI subcommand.
 */
@CliFixture(root = "fixtures/introduce-indirection/static-method/input/MathUtils.java")
class CliIntroduceIndirectionTest {

    @Test
    void apply_writes_indirection_method(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "introduce-indirection",
                "--file", bed.root().toString(),
                "--line", "3", "--column", "26",
                "--name", "computeSquare");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        String result = Files.readString(bed.root());
        assertThat(result).as("Original method should be unchanged").contains("public static int square(int x)");
        assertThat(result).as("Indirection method should delegate to the original")
                .contains("public static int computeSquare(int x)")
                .contains("return square(x);");
    }
}
