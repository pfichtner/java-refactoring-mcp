package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the CLI layer.
 *
 * The CLI is exercised end-to-end: real Picocli argument parsing, real JDT
 * engine, real file I/O (for the apply test). Approved .txt files capture
 * the exact stdout so regressions are caught immediately.
 */
@CliFixture(root = "fixtures/projects/rename-method/pom.xml")
class CliRenameTest {

    @Test
    void apply_writes_changed_files(CliTestBed bed) throws Exception {
        Path calcFile = bed.root().resolve("src/main/java/com/example/Calculator.java");

        int exit = bed.cli().execute(
                "rename",
                "--file", calcFile.toString(),
                "--line", "4", "--column", "16",
                "--name", "plus");

        assertThat(exit).as("Expected exit code 0").isEqualTo(0);

        // Verify summary output
        Approvals.verify(bed.out().toString());

        // Verify the actual file contents were updated (golden-master the results)
        String updatedCalc = Files.readString(calcFile);
        String updatedApp  = Files.readString(
                bed.root().resolve("src/main/java/com/example/App.java"));

        assertThat(updatedCalc).as("Declaration should be renamed").doesNotContain(" add(");
        assertThat(updatedCalc).as("Declaration should be 'plus'").contains(" plus(");
        assertThat(updatedApp).as("Call site should be renamed").doesNotContain(".add(");
        assertThat(updatedApp).as("Call site should be 'plus'").contains(".plus(");
    }

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
