package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code introduce-factory} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/static-factory/pom.xml")
class CliIntroduceStaticFactoryTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path counterFile = bed.root().resolve("src/main/java/com/example/Counter.java");

        int exit = bed.cli().execute(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        bed.assertUnchanged();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void dry_run_with_private_constructor(CliTestBed bed) throws Exception {
        Path counterFile = bed.root().resolve("src/main/java/com/example/Counter.java");

        int exit = bed.cli().execute(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of",
                "--private-constructor",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(bed.out().toString()).as("Preview should show a private constructor").contains("private Counter(");

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_factory_method_and_call_sites(CliTestBed bed) throws Exception {
        Path counterFile = bed.root().resolve("src/main/java/com/example/Counter.java");
        Path appFile = bed.root().resolve("src/main/java/com/example/App.java");

        int exit = bed.cli().execute(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(counterFile)).as("Factory should be declared")
                .contains("public static Counter of(int value, String label)");
        assertThat(Files.readString(appFile)).as("Call sites should use the factory")
                .contains("Counter.of(0, \"start\")");
    }
}
