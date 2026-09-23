package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.FileChange;

import picocli.CommandLine.Option;

/** Shared skeleton for single-file, single-String-result refactoring commands. */
abstract class SingleFileCommand extends AbstractRefactoringCommand {

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file to refactor.")
    Path file;

    private Path absFile;

    @Override
    protected int validate() {
        absFile = checkedFile(file);
        return absFile == null ? 1 : 0;
    }

    /** Resolves the target offset inside {@code source}. Subclasses add the locator options. */
    protected abstract int resolveOffset(String source, String unitName);

    /** Applies the refactoring and returns the modified source. */
    protected abstract String transform(String source, String unitName, int offset) throws Exception;

    /** One-line success message printed after writing (receives the absolute file path). */
    protected abstract String successMessage(Path absFile);

    @Override
    protected List<FileChange> refactor() throws Exception {
        String source = Files.readString(absFile);
        int    offset = resolveOffset(source, absFile.getFileName().toString());
        String result = transform(source, absFile.getFileName().toString(), offset);
        return List.of(new FileChange(absFile, absFile, result));
    }

    @Override
    protected void printDryRun(List<FileChange> changes) {
        var out = spec.commandLine().getOut();
        out.println("Dry run — no file written.");
        out.println();
        out.println(changes.get(0).newSource());
    }

    @Override
    protected void printApplyReport(List<FileChange> changes) {
        spec.commandLine().getOut().println(successMessage(changes.get(0).newPath()));
    }
}