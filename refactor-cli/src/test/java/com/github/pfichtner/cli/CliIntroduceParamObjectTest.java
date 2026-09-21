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

        Path srcRoot     = tmp.resolve("src/main/java/com/example");
        Path printerFile = srcRoot.resolve("Printer.java");
        Path coordFile   = srcRoot.resolve("Coordinate.java");
        Path appFile     = srcRoot.resolve("App.java");

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
        assertThat(coordFile).as("Coordinate.java must be created").exists();

        String snapshot = snapshot(
                "App.java",     Files.readString(appFile),
                "Coordinate.java", Files.readString(coordFile),
                "Printer.java", Files.readString(printerFile));
        Approvals.verify(snapshot);
    }

    private static String snapshot(String... nameAndContent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nameAndContent.length; i += 2) {
            sb.append("=== ").append(nameAndContent[i]).append(" ===\n");
            sb.append(nameAndContent[i + 1].stripTrailing()).append("\n\n");
        }
        return sb.toString().stripTrailing();
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
