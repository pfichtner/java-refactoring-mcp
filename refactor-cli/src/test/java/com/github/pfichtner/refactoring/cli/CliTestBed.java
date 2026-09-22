package com.github.pfichtner.refactoring.cli;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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
            return stream
            		.filter(Files::isRegularFile)
                    .collect(toMap(identity(), CliTestBed::contentOf));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    List<Path> changedFiles() {
        return snapshot.entrySet().stream()
                .filter(e -> !contentOf(e.getKey()).equals(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static String contentOf(Path path) {
    	try { return Files.readString(path); }
    	catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** Temp-dir copy of the fixture: project root dir, or single fixture file. */
    Path root() { return root; }

    /** Captured stdout+stderr of all CLI invocations in this test. */
    StringWriter out() { return out; }

    /** Pre-configured CLI ready to execute subcommands. */
    CommandLine cli() { return cli; }

    /**
     * Executes a dry-run CLI command, asserts exit code 0 and no files modified,
     * then returns the captured output — ready for {@code Approvals.verify()}.
     */
    String preview(String... args) {
        int exit = cli.execute(args);
        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        assertThat(changedFiles()).as("Dry-run must not modify any file").isEmpty();
        return out.toString();
    }

    /**
     * Executes an apply CLI command, asserts exit code 0, then returns the
     * contents of every file that was created, modified, or deleted — ready for
     * {@code Approvals.verify()}.
     */
    String apply(String... args) throws IOException {
        int exit = cli.execute(args);
        assertThat(exit).as("Expected exit code 0: " + out).isEqualTo(0);
        Path dir = Files.isDirectory(root) ? root : root.getParent();
        Map<Path, String> current;
        try (var stream = Files.walk(dir)) {
            current = stream.filter(Files::isRegularFile)
                    .collect(toMap(identity(), CliTestBed::contentOf));
        }
        TreeSet<Path> affected = new TreeSet<>();
        for (Path p : current.keySet()) {
            String orig = snapshot.get(p);
            if (!current.get(p).equals(orig != null ? orig : "")) affected.add(p);
        }
        snapshot.keySet().stream().filter(p -> !current.containsKey(p)).forEach(affected::add);
        StringBuilder sb = new StringBuilder();
        for (Path p : affected) {
            String rel = dir.relativize(p).toString();
            if (current.containsKey(p)) {
                sb.append("=== ").append(rel).append(" ===\n")
                  .append(current.get(p).stripTrailing()).append("\n\n");
            } else {
                sb.append("=== ").append(rel).append(" [deleted] ===\n\n");
            }
        }
        return sb.toString().stripTrailing();
    }
}
