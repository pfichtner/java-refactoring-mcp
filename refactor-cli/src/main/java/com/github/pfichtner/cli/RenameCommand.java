package com.github.pfichtner.cli;

import com.github.pfichtner.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI subcommand for rename refactoring. Logic lives in {@link JdtRenamer}.
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

    @Mixin LocatorOptions locator;

    @Option(names = {"--name", "-n"}, required = true,
            description = "New name for the symbol.")
    String newName;

    @Option(names = "--dry-run",
            description = "Preview changes without writing to disk.")
    boolean dryRun;

    @Mixin ProjectOptions project;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }

        String source = Files.readString(absFile);
        int offset = locator.resolveOffset(source, absFile.getFileName().toString());

        Map<Path, String> changed = JdtRenamer.rename(
                project.resolve(absFile), absFile, offset, newName);

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
}
