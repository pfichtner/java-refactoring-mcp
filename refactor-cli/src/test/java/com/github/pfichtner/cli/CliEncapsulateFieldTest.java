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
 * Integration tests for the {@code encapsulate-field} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/encapsulate-field/pom.xml")
class CliEncapsulateFieldTest {

    @Test
    void dry_run_prints_getter_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path personFile = root.resolve("src/main/java/com/example/Person.java");
        String before = Files.readString(personFile);

        int exit = cli.execute(
                "encapsulate-field",
                "--file", personFile.toString(),
                "--field", "name",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(personFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void dry_run_prints_getter_and_setter_preview(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path personFile = root.resolve("src/main/java/com/example/Person.java");

        int exit = cli.execute(
                "encapsulate-field",
                "--file", personFile.toString(),
                "--field", "name",
                "--setter",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(out.toString()).as("Preview should show a setter").contains("setName");

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_getter_and_updates_call_sites(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path personFile = tmp.resolve("src/main/java/com/example/Person.java");
        Path appFile = tmp.resolve("src/main/java/com/example/App.java");

        int exit = cli.execute(
                "encapsulate-field",
                "--file", personFile.toString(),
                "--field", "name");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(personFile)).as("Field should become private with getter")
                .contains("private String name;")
                .contains("public String getName()");
        assertThat(Files.readString(appFile)).as("Read access should use the getter").contains("p.getName()");
    }
}
