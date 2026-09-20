package com.github.pfichtner.cli;

import com.github.pfichtner.FileChange;
import com.github.pfichtner.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

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

        List<FileChange> changed = JdtRenamer.rename(
                project.resolve(absFile), absFile, offset, newName);

        if (dryRun) {
            printDryRun(changed);
        } else {
            applyChanges(changed);
        }
        return 0;
    }

    private void printDryRun(List<FileChange> changed) {
        var out = spec.commandLine().getOut();
        out.println("Dry run — no files written.");
        if (changed.isEmpty()) {
            out.println("No changes.");
            return;
        }
        String names = changed.stream()
                .map(fc -> fc.newPath().getFileName().toString())
                .sorted()
                .collect(Collectors.joining(", "));
        out.println("Would change (" + changed.size() + "): " + names);
        changed.stream()
                .sorted(Comparator.comparing(fc -> fc.newPath().toString()))
                .forEach(fc -> {
                    out.println();
                    out.println("=== " + fc.newPath().getFileName() + " ===");
                    out.println(fc.newSource().stripTrailing());
                });
    }

    private void applyChanges(List<FileChange> changed) throws IOException {
        var out = spec.commandLine().getOut();
        if (changed.isEmpty()) {
            out.println("No changes.");
            return;
        }
        for (FileChange fc : changed) {
            Files.createDirectories(fc.newPath().getParent());
            Files.writeString(fc.newPath(), fc.newSource());
            if (fc.pathChanged()) Files.deleteIfExists(fc.oldPath());
        }
        out.println("Renamed in " + changed.size() + " file(s):");
        changed.stream()
                .sorted(Comparator.comparing(fc -> fc.newPath().toString()))
                .forEach(fc -> {
                    if (fc.pathChanged())
                        out.println("  " + fc.oldPath().getFileName() + " → " + fc.newPath().getFileName());
                    else
                        out.println("  " + fc.newPath().getFileName());
                });
    }
}
