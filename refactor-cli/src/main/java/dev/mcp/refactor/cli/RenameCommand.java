package dev.mcp.refactor.cli;

import dev.mcp.refactor.JdtRenamer;
import dev.mcp.refactor.project.MavenProject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI subcommand for rename refactoring.
 *
 * All refactoring logic lives in {@link JdtRenamer} and {@link MavenProject}.
 * This class is thin: parse args → call engine → report results.
 */
@Command(
    name = "rename",
    mixinStandardHelpOptions = true,
    description = "Rename the Java symbol at the given file location."
)
public class RenameCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the symbol to rename.")
    Path file;

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number of the symbol.")
    int line;

    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number of the symbol.")
    int column;

    @Option(names = {"--name", "-n"}, required = true,
            description = "New name for the symbol.")
    String newName;

    @Option(names = "--dry-run",
            description = "Preview changes without writing to disk.")
    boolean dryRun;

    @Option(names = "--project",
            description = "Maven project root (auto-detected from --file if omitted).")
    Path projectRoot;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }

        String source = Files.readString(absFile);
        int offset = toOffset(source, line, column);

        Path root = projectRoot != null
                ? projectRoot.toAbsolutePath().normalize()
                : findProjectRoot(absFile);

        Map<Path, String> changed = JdtRenamer.rename(new MavenProject(root), absFile, offset, newName);

        if (dryRun) {
            printDryRun(changed);
        } else {
            applyChanges(changed);
        }
        return 0;
    }

    private void printDryRun(Map<Path, String> changed) {
        var out = spec.commandLine().getOut();
        out.println("Dry run — no files written.");
        if (changed.isEmpty()) {
            out.println("No changes.");
            return;
        }
        String names = changed.keySet().stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .reduce((a, b) -> a + ", " + b).orElse("");
        out.println("Changed files (" + changed.size() + "): " + names);
        changed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    out.println();
                    out.println("=== " + e.getKey().getFileName() + " ===");
                    out.println(e.getValue().stripTrailing());
                });
    }

    private void applyChanges(Map<Path, String> changed) throws IOException {
        var out = spec.commandLine().getOut();
        if (changed.isEmpty()) {
            out.println("No changes.");
            return;
        }
        for (Map.Entry<Path, String> entry : changed.entrySet()) {
            Files.writeString(entry.getKey(), entry.getValue());
        }
        out.println("Renamed in " + changed.size() + " file(s):");
        changed.keySet().stream()
                .sorted()
                .forEach(p -> out.println("  " + p.getFileName()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static int toOffset(String source, int line, int col) {
        return JdtRenamer.toOffset(source, line, col);
    }

    static Path findProjectRoot(Path file) {
        Path dir = file.getParent();
        while (dir != null) {
            if (Files.exists(dir.resolve("pom.xml"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "No pom.xml found by walking up from: " + file);
    }
}
