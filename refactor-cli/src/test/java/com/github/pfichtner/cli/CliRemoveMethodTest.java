package com.github.pfichtner.cli;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@code remove-method} CLI subcommand.
 */
class CliRemoveMethodTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliRemoveMethodTest.class.getClassLoader()
                            .getResource("fixtures/projects/remove-method/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path printableFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Printable.java");
        String before = Files.readString(printableFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
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
    void apply_writes_cascade(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path printableFile = tmp.resolve("src/main/java/com/example/Printable.java");
        Path documentFile  = tmp.resolve("src/main/java/com/example/Document.java");
        Path reportFile    = tmp.resolve("src/main/java/com/example/Report.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
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
    void no_cascade_leaves_subclass_override(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path documentFile = tmp.resolve("src/main/java/com/example/Document.java");
        Path reportFile   = tmp.resolve("src/main/java/com/example/Report.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
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

    private static CommandLine cli(StringWriter out) {
        CommandLine cmd = new CommandLine(new Main());
        cmd.setOut(new PrintWriter(out, true));
        cmd.setErr(new PrintWriter(out, true));
        cmd.setExecutionExceptionHandler((ex, c, pr) -> {
            c.getErr().println("Error: " + ex.getMessage());
            return 1;
        });
        return cmd;
    }

    private static void copyTree(Path src, Path dst) throws Exception {
        try (var stream = Files.walk(src)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                Path target = dst.resolve(src.relativize(p));
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
    }
}
