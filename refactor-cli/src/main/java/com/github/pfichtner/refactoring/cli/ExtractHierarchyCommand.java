package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Shared skeleton for extract-superclass and extract-interface commands. */
abstract class ExtractHierarchyCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Option(names = {"--name", "-n"}, required = true) String name;
    @Option(names = "--output-file", required = true,
            description = "Output path for the new file.") Path outputFile;
    @Option(names = "--methods", split = ",",
            description = "Methods to include (default: all public non-static).") List<String> methods = List.of();
    @Option(names = "--dry-run") boolean dryRun;

    /** Applies the extraction and returns [modifiedSource, newTypeSource]. */
    protected abstract String[] extract(String source, String unitName) throws Exception;
    protected abstract String label(); // "superclass" or "interface"

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source = Files.readString(absFile);
        String[] result = extract(source, absFile.getFileName().toString());

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            out.println("\n=== " + absFile.getFileName() + " (modified) ===");
            out.println(result[0].stripTrailing());
            out.println("\n=== " + outputFile.getFileName() + " (new) ===");
            out.println(result[1].stripTrailing());
        } else {
            Files.writeString(absFile, result[0]);
            Files.createDirectories(outputFile.toAbsolutePath().getParent());
            Files.writeString(outputFile.toAbsolutePath(), result[1]);
            out.println("Extracted " + label() + " '" + name + "':");
            out.println("  Modified: " + absFile.getFileName());
            out.println("  Created:  " + outputFile.getFileName());
        }
        return 0;
    }
}
