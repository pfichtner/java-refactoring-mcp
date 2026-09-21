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
 * Integration tests for the {@code encapsulate-field} CLI subcommand.
 */
class CliEncapsulateFieldTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliEncapsulateFieldTest.class.getClassLoader()
                            .getResource("fixtures/projects/encapsulate-field/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_getter_preview_without_writing() throws Exception {
        Path personFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Person.java");
        String before = Files.readString(personFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "encapsulate-field",
                "--file", personFile.toString(),
                "--field", "name",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(personFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void dry_run_prints_getter_and_setter_preview() throws Exception {
        Path personFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Person.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
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
    void apply_writes_getter_and_updates_call_sites(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path personFile = tmp.resolve("src/main/java/com/example/Person.java");
        Path appFile = tmp.resolve("src/main/java/com/example/App.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "encapsulate-field",
                "--file", personFile.toString(),
                "--field", "name");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(personFile)).as("Field should become private with getter")
                .contains("private String name;")
                .contains("public String getName()");
        assertThat(Files.readString(appFile)).as("Read access should use the getter").contains("p.getName()");
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