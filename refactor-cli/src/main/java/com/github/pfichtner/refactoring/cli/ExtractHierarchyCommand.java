package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.FileChange;

import picocli.CommandLine.Option;

/** Shared skeleton for extract-superclass and extract-interface commands. */
abstract class ExtractHierarchyCommand extends AbstractRefactoringCommand {

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Option(names = {"--name", "-n"}, required = true) String name;
    @Option(names = "--output-file", required = true,
            description = "Output path for the new file.") Path outputFile;
    @Option(names = "--methods", split = ",",
            description = "Methods to include (default: all public non-static).") List<String> methods = List.of();

    private Path absFile;
    private Path newFile;

    @Override
    protected int validate() {
        absFile = checkedFile(file);
        return absFile == null ? 1 : 0;
    }

    /** Applies the extraction and returns [modifiedSource, newTypeSource]. */
    protected abstract String[] extract(String source, String unitName) throws Exception;
    protected abstract String label(); // "superclass" or "interface"

    @Override
    protected List<FileChange> refactor() throws Exception {
        String source = Files.readString(absFile);
        String[] result = extract(source, absFile.getFileName().toString());
        newFile = outputFile.toAbsolutePath().normalize();
        return List.of(
                new FileChange(absFile, absFile, result[0]),
                new FileChange(newFile, newFile, result[1]));
    }

    @Override
    protected void printDryRun(List<FileChange> changes) {
        var out = spec.commandLine().getOut();
        out.println("Dry run — no files written.");
        out.println("\n=== " + absFile.getFileName() + " (modified) ===");
        out.println(changes.get(0).newSource().stripTrailing());
        out.println("\n=== " + newFile.getFileName() + " (new) ===");
        out.println(changes.get(1).newSource().stripTrailing());
    }

    @Override
    protected void printApplyReport(List<FileChange> changes) {
        var out = spec.commandLine().getOut();
        out.println("Extracted " + label() + " '" + name + "':");
        out.println("  Modified: " + absFile.getFileName());
        out.println("  Created:  " + newFile.getFileName());
    }
}