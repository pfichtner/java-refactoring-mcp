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
 * Integration tests for the {@code push-down-field} CLI subcommand.
 */
class CliPushDownFieldTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliPushDownFieldTest.class.getClassLoader()
                            .getResource("fixtures/projects/push-down-field/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path vehicleFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Vehicle.java");
        String before = Files.readString(vehicleFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "push-down-field",
                "--file", vehicleFile.toString(),
                "--field", "maxSpeed",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(vehicleFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_moves_field_to_subclasses(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path vehicleFile = tmp.resolve("src/main/java/com/example/Vehicle.java");
        Path carFile = tmp.resolve("src/main/java/com/example/Car.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "push-down-field",
                "--file", vehicleFile.toString(),
                "--field", "maxSpeed");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(vehicleFile)).as("Superclass should lose the field").doesNotContain("maxSpeed");
        assertThat(Files.readString(carFile)).as("Subclass should gain the field").contains("int maxSpeed");
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