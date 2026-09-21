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
 * Integration tests for the {@code introduce-param} CLI subcommand.
 */
class CliIntroduceParamTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliIntroduceParamTest.class.getClassLoader()
                            .getResource("fixtures/projects/introduce-param/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path greeterFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Greeter.java");
        String before = Files.readString(greeterFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "introduce-param",
                "--file", greeterFile.toString(),
                "--start-line", "5", "--start-column", "40",
                "--end-line", "5", "--end-column", "47",
                "--name", "whom",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(greeterFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_new_parameter(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path greeterFile = tmp.resolve("src/main/java/com/example/Greeter.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "introduce-param",
                "--file", greeterFile.toString(),
                "--start-line", "5", "--start-column", "40",
                "--end-line", "5", "--end-column", "47",
                "--name", "whom");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(greeterFile)).as("Method should declare the new parameter")
                .contains("public void greet(java.lang.String whom)");
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