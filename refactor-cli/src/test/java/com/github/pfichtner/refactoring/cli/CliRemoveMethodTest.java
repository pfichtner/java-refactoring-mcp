package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code remove-method} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/remove-method/pom.xml")
class CliRemoveMethodTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path printableFile = bed.root().resolve("src/main/java/com/example/Printable.java");

        int exit = bed.cli().execute(
                "remove-method",
                "--file", printableFile.toString(),
                "--method", "print",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        bed.assertUnchanged();

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_writes_cascade(CliTestBed bed) throws Exception {
        Path printableFile = bed.root().resolve("src/main/java/com/example/Printable.java");
        Path documentFile  = bed.root().resolve("src/main/java/com/example/Document.java");
        Path reportFile    = bed.root().resolve("src/main/java/com/example/Report.java");

        int exit = bed.cli().execute(
                "remove-method",
                "--file", printableFile.toString(),
                "--method", "print");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(printableFile))
                .as("Interface should no longer declare print()").doesNotContain("void print");
        assertThat(Files.readString(documentFile))
                .as("Document's print() override should be removed").doesNotContain("void print");
        assertThat(Files.readString(reportFile))
                .as("Report's print() override should be removed").doesNotContain("void print");
    }

    @Test
    void no_cascade_leaves_subclass_override(CliTestBed bed) throws Exception {
        Path documentFile = bed.root().resolve("src/main/java/com/example/Document.java");
        Path reportFile   = bed.root().resolve("src/main/java/com/example/Report.java");

        int exit = bed.cli().execute(
                "remove-method",
                "--file", documentFile.toString(),
                "--method", "print",
                "--no-cascade");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(documentFile))
                .as("Document's print() should be removed").doesNotContain("void print");
        assertThat(Files.readString(reportFile))
                .as("Report's print() override should remain").contains("void print");
    }
}
