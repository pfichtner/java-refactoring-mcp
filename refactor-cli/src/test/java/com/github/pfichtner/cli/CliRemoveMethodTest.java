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
 * Integration tests for the {@code remove-method} CLI subcommand.
 */
@CliTestBed(root = "fixtures/projects/remove-method/pom.xml")
class CliRemoveMethodTest {

    @Test
    void dry_run_prints_preview_without_writing(CommandLine cli, StringWriter out, Path root) throws Exception {
        Path printableFile = root.resolve("src/main/java/com/example/Printable.java");
        String before = Files.readString(printableFile);

        int exit = cli.execute(
                "remove-method",
                "--file", printableFile.toString(),
                "--method", "print",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(printableFile))
                .as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_cascade(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path printableFile = tmp.resolve("src/main/java/com/example/Printable.java");
        Path documentFile  = tmp.resolve("src/main/java/com/example/Document.java");
        Path reportFile    = tmp.resolve("src/main/java/com/example/Report.java");

        int exit = cli.execute(
                "remove-method",
                "--file", printableFile.toString(),
                "--method", "print");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(printableFile))
                .as("Interface should no longer declare print()").doesNotContain("void print");
        assertThat(Files.readString(documentFile))
                .as("Document's print() override should be removed").doesNotContain("void print");
        assertThat(Files.readString(reportFile))
                .as("Report's print() override should be removed").doesNotContain("void print");
    }

    @Test
    void no_cascade_leaves_subclass_override(CommandLine cli, StringWriter out, Path root, @TempDir Path tmp) throws Exception {
        CliTestSupport.copyTree(root, tmp);

        Path documentFile = tmp.resolve("src/main/java/com/example/Document.java");
        Path reportFile   = tmp.resolve("src/main/java/com/example/Report.java");

        int exit = cli.execute(
                "remove-method",
                "--file", documentFile.toString(),
                "--method", "print",
                "--no-cascade");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(documentFile))
                .as("Document's print() should be removed").doesNotContain("void print");
        assertThat(Files.readString(reportFile))
                .as("Report's print() override should remain").contains("void print");
    }
}
