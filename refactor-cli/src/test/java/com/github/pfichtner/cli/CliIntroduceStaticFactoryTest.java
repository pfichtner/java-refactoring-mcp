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
 * Integration tests for the {@code introduce-factory} CLI subcommand.
 */
class CliIntroduceStaticFactoryTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliIntroduceStaticFactoryTest.class.getClassLoader()
                            .getResource("fixtures/projects/static-factory/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path counterFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Counter.java");
        String before = Files.readString(counterFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(counterFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void dry_run_with_private_constructor() throws Exception {
        Path counterFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Counter.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of",
                "--private-constructor",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(out.toString()).as("Preview should show a private constructor").contains("private Counter(");

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_factory_method_and_call_sites(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path counterFile = tmp.resolve("src/main/java/com/example/Counter.java");
        Path appFile = tmp.resolve("src/main/java/com/example/App.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "introduce-factory",
                "--file", counterFile.toString(),
                "--line", "7", "--column", "5",
                "--name", "of");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(counterFile)).as("Factory should be declared")
                .contains("public static Counter of(int value, String label)");
        assertThat(Files.readString(appFile)).as("Call sites should use the factory")
                .contains("Counter.of(0, \"start\")");
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