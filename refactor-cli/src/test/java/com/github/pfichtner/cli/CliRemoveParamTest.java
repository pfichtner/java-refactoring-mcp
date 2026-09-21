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
 * Integration tests for the {@code remove-param} CLI subcommand.
 */
class CliRemoveParamTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliRemoveParamTest.class.getClassLoader()
                            .getResource("fixtures/projects/remove-param/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path computationFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Computation.java");
        String before = Files.readString(computationFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "remove-param",
                "--file", computationFile.toString(),
                "--method", "add",
                "--parameter", "c",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(computationFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_removed_parameter(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path computationFile = tmp.resolve("src/main/java/com/example/Computation.java");
        Path appFile = tmp.resolve("src/main/java/com/example/App.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "remove-param",
                "--file", computationFile.toString(),
                "--method", "add",
                "--parameter", "c");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(computationFile)).as("Declaration should lose the parameter")
                .contains("public int add(int a, int b)");
        assertThat(Files.readString(appFile)).as("Call site should drop the argument")
                .contains("calc.add(1, 2)");
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