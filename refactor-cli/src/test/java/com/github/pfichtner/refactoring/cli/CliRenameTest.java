package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Error-case and unit tests for the {@code rename} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/rename-method/pom.xml")
class CliRenameTest {

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void missing_file_returns_exit_code_1(CliTestBed bed) throws Exception {
        int exit = bed.cli().execute(
                "rename",
                "--file", "/nonexistent/Foo.java",
                "--line", "1", "--column", "1",
                "--name", "bar");

        assertThat(exit).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // toOffset unit tests
    // -------------------------------------------------------------------------

    @Test
    void toOffset_first_line() {
        assertThat(com.github.pfichtner.refactoring.JdtRenamer.toOffset("abcde", 1, 5)).isEqualTo(4);
    }

    @Test
    void toOffset_second_line() {
        // "abcd\nefgh": line 2 starts at index 5; col 2 → index 6 ('f')
        assertThat(com.github.pfichtner.refactoring.JdtRenamer.toOffset("abcd\nefgh", 2, 2)).isEqualTo(6);
    }
}
