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
 * Integration tests for the {@code push-down} CLI subcommand (method variant).
 */
class CliPushDownMethodTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliPushDownMethodTest.class.getClassLoader()
                            .getResource("fixtures/projects/push-down-method/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path shapeFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Shape.java");
        String before = Files.readString(shapeFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "push-down",
                "--file", shapeFile.toString(),
                "--method", "area",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(shapeFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_moves_method_to_subclasses(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path shapeFile = tmp.resolve("src/main/java/com/example/Shape.java");
        Path circleFile = tmp.resolve("src/main/java/com/example/Circle.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "push-down",
                "--file", shapeFile.toString(),
                "--method", "area");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(shapeFile)).as("Superclass should lose the method").doesNotContain("double area()");
        assertThat(Files.readString(circleFile)).as("Subclass should gain the method").contains("double area()");
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