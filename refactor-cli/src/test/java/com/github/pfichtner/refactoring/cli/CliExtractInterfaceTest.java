package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code extract-interface} CLI subcommand.
 */
@CliFixture(root = "fixtures/extract-interface/simple/input/Calculator.java")
class CliExtractInterfaceTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {

        int exit = bed.cli().execute(
                "extract-interface",
                "--file", bed.root().toString(),
                "--name", "Arithmetic",
                "--interface-file", bed.root().getParent().resolve("Arithmetic.java").toString(),
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        bed.assertUnchanged();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_interface_file(CliTestBed bed) throws Exception {
        Path interfaceFile = bed.root().getParent().resolve("Arithmetic.java");

        int exit = bed.cli().execute(
                "extract-interface",
                "--file", bed.root().toString(),
                "--name", "Arithmetic",
                "--interface-file", interfaceFile.toString());

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(bed.root())).as("Class should implement the interface")
                .contains("implements Arithmetic");
        assertThat(Files.exists(interfaceFile)).as("Interface file should be written").isTrue();
        assertThat(Files.readString(interfaceFile))
                .as("Interface should declare the extracted methods")
                .contains("int add(int a, int b)");
    }
}
