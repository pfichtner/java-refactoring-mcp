package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Error-case tests for the {@code extract} CLI subcommand.
 */
@CliFixture(root = "fixtures/extract/simple/input/Greeter.java")
class CliExtractTest {

    @Test
    void missing_file_returns_exit_code_1(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "extract",
                "--file", "/nonexistent/Greeter.java",
                "--start-line", "3", "--start-column", "9",
                "--end-line", "5", "--end-column", "9",
                "--name", "sayHi");

        assertThat(exit).isEqualTo(1);
    }
}
