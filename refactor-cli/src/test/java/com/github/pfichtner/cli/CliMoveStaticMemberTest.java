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
 * Integration tests for the {@code move-static} CLI subcommand.
 */
class CliMoveStaticMemberTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliMoveStaticMemberTest.class.getClassLoader()
                            .getResource("fixtures/projects/move-static/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path mathUtilsFile = FIXTURE_ROOT.resolve("src/main/java/com/example/MathUtils.java");
        String before = Files.readString(mathUtilsFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "move-static",
                "--file", mathUtilsFile.toString(),
                "--line", "5", "--column", "5",
                "--target", "com.example.Helpers",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(mathUtilsFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_moves_static_member(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path mathUtilsFile = tmp.resolve("src/main/java/com/example/MathUtils.java");
        Path helpersFile = tmp.resolve("src/main/java/com/example/Helpers.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "move-static",
                "--file", mathUtilsFile.toString(),
                "--line", "5", "--column", "5",
                "--target", "com.example.Helpers");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(helpersFile)).as("Target class should gain the static member")
                .contains("public static int square");
        assertThat(Files.readString(mathUtilsFile)).as("Source class should lose the member")
                .doesNotContain("square");
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