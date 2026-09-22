package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code introduce-factory} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/static-factory/pom.xml")
class CliIntroduceStaticFactoryTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path counterFile = root.resolve("src/main/java/com/example/Counter.java");
        String before = Files.readString(counterFile);

        int exit = cli.execute(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(counterFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void dry_run_with_private_constructor(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path counterFile = root.resolve("src/main/java/com/example/Counter.java");

        int exit = cli.execute(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of",
                "--private-constructor",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(out.toString()).as("Preview should show a private constructor").contains("private Counter(");

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_factory_method_and_call_sites(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path counterFile = tmp.resolve("src/main/java/com/example/Counter.java");
        Path appFile = tmp.resolve("src/main/java/com/example/App.java");

        int exit = cli.execute(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(counterFile)).as("Factory should be declared")
                .contains("public static Counter of(int value, String label)");
        assertThat(Files.readString(appFile)).as("Call sites should use the factory")
                .contains("Counter.of(0, \"start\")");
    }
}
