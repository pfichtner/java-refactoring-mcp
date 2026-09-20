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

class CliIntroduceParamObjectTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliIntroduceParamObjectTest.class.getClassLoader()
                            .getResource("fixtures/projects/introduce-param-object/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_record_prints_preview_without_writing() throws Exception {
        Path printerFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Printer.java");
        String before = Files.readString(printerFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "introduce-param-object",
                "--file", printerFile.toString(),
                "--line", "4",
                "--column", "17",
                "--params", "x,y",
                "--class-name", "Coordinate",
                "--record",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(printerFile))
                .as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_record_writes_record_and_updates_call_sites(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path printerFile = tmp.resolve("src/main/java/com/example/Printer.java");
        Path coordFile   = tmp.resolve("src/main/java/com/example/Coordinate.java");
        Path appFile     = tmp.resolve("src/main/java/com/example/App.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "introduce-param-object",
                "--file", printerFile.toString(),
                "--line", "4",
                "--column", "17",
                "--params", "x,y",
                "--class-name", "Coordinate",
                "--record");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(coordFile))
                .as("Coordinate.java should be a record").contains("record Coordinate(int x, int y)");
        assertThat(Files.readString(printerFile))
                .as("Printer.java should use record-style accessors").contains("coordinate.x()");
        assertThat(Files.readString(appFile))
                .as("App.java should use canonical constructor")
                .contains("new Coordinate(");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
