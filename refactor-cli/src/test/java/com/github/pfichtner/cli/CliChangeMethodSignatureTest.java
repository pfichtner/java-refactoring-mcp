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
 * Integration tests for the {@code change-method-signature} CLI subcommand.
 */
class CliChangeMethodSignatureTest {

    private static final Path FIXTURE_ROOT;

    static {
        try {
            FIXTURE_ROOT = Path.of(
                    CliChangeMethodSignatureTest.class.getClassLoader()
                            .getResource("fixtures/projects/change-method-signature/pom.xml")
                            .toURI()
            ).getParent();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    void dry_run_prints_preview_without_writing() throws Exception {
        Path converterFile = FIXTURE_ROOT.resolve("src/main/java/com/example/Converter.java");
        String before = Files.readString(converterFile);

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "change-method-signature",
                "--file", converterFile.toString(),
                "--method", "convert",
                "--param-order", "1,0",
                "--return-type", "Object",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(converterFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(out.toString());
    }

    @Test
    void apply_writes_new_signature_and_call_sites(@TempDir Path tmp) throws Exception {
        copyTree(FIXTURE_ROOT, tmp);

        Path converterFile = tmp.resolve("src/main/java/com/example/Converter.java");
        Path appFile = tmp.resolve("src/main/java/com/example/App.java");

        StringWriter out = new StringWriter();
        int exit = cli(out).execute(
                "change-method-signature",
                "--file", converterFile.toString(),
                "--method", "convert",
                "--param-order", "1,0",
                "--return-type", "Object");

        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(Files.readString(converterFile)).as("Declaration should use the new return type")
                .contains("Object convert(String prefix, int value)");
        assertThat(Files.readString(appFile)).as("Call sites should be reordered")
                .contains(".convert(\"val:\", 42)");
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