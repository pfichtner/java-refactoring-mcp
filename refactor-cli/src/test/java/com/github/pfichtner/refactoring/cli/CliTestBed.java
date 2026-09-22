package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import picocli.CommandLine;

class CliTestBed {

    private final Path root;
    private final Map<Path, String> snapshot;
    private final StringWriter out;
    private final CommandLine cli;

    CliTestBed(Path root) {
        this.root = root;
        this.snapshot = takeSnapshot(Files.isDirectory(root) ? root : root.getParent());
        this.out = new StringWriter();
        CommandLine cmd = new CommandLine(new Main());
        PrintWriter pw = new PrintWriter(out, true);
        cmd.setOut(pw);
        cmd.setErr(pw);
        cmd.setExecutionExceptionHandler((ex, c, pr) -> {
            c.getErr().println("Error: " + ex.getMessage());
            return 1;
        });
        this.cli = cmd;
    }

    private static Map<Path, String> takeSnapshot(Path dir) {
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile)
                    .collect(Collectors.toMap(p -> p, p -> {
                        try { return Files.readString(p); }
                        catch (IOException e) { throw new UncheckedIOException(e); }
                    }));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void assertUnchanged() {
        snapshot.forEach((path, content) -> {
            try {
                assertThat(Files.readString(path))
                        .as("Dry-run must not modify " + path.getFileName())
                        .isEqualTo(content);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Temp-dir copy of the fixture: project root dir, or single fixture file. */
    Path root() { return root; }

    /** Captured stdout+stderr of all CLI invocations in this test. */
    StringWriter out() { return out; }

    /** Pre-configured CLI ready to execute subcommands. */
    CommandLine cli() { return cli; }
}
