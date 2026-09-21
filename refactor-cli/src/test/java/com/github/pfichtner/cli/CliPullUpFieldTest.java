package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code pull-up-field} CLI subcommand.
 */
class CliPullUpFieldTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliPullUpFieldTest.class.getClassLoader()
                            .getResource("fixtures/projects/pull-up-field/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path dogFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Dog.java");
        String before = Files.readString(dogFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "pull-up-field",
                "--file", dogFile.toString(),
                "--field", "breed",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(dogFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_moves_field_to_superclass(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path dogFile = tmp.resolve("src/main/java/com/example/Dog.java");
        Path animalFile = tmp.resolve("src/main/java/com/example/Animal.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "pull-up-field",
                "--file", dogFile.toString(),
                "--field", "breed");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(animalFile)).as("Superclass should gain the field").contains("String breed");
        assertThat(Files.readString(dogFile)).as("Subclass should lose the field").doesNotContain("String breed");
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