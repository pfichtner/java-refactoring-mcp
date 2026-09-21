package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

/**
 * Integration tests for the {@code rename-package} CLI subcommand.
 */
class CliRenamePackageTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliRenamePackageTest.class.getClassLoader()
                            .getResource("fixtures/projects/rename-package/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path calculatorFile = FIXTURE_ROOT.resolve("src/main/java/com/example/service/Calculator.java");
        String before = Files.readString(calculatorFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "rename-package",
                "--project", FIXTURE_ROOT.toString(),
                "--old-package", "com.example.service",
                "--new-package", "com.example.util",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(calculatorFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString(), new Options().withScrubber(scrubProjectRoot()));
    }

    private static Scrubber scrubProjectRoot() {
        String projectRoot = Path.of("").toAbsolutePath().normalize().toString();
        return input -> input.replace(projectRoot + java.io.File.separator, "{ROOT}/");
    }

    @Test
    void apply_moves_package_directory(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path movedFile = tmp.resolve("src/main/java/com/example/util/Calculator.java");
        Path appFile = tmp.resolve("src/main/java/com/example/app/App.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "rename-package",
                "--project", tmp.toString(),
                "--old-package", "com.example.service",
                "--new-package", "com.example.util");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.exists(movedFile)).as("Class should move under the new package").isTrue();
        assertThat(Files.readString(appFile)).as("Import should be updated")
                .contains("import com.example.util.Calculator;");
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