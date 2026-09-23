package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtRenamer;
import com.github.pfichtner.refactoring.SourceUnit;

import picocli.CommandLine.Option;

/** Shared skeleton for single-file, selection-range refactoring commands (start/end line+column). */
abstract class SelectionCommand extends AbstractRefactoringCommand {

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file to refactor.")
    Path file;

    @Option(names = "--start-line",   required = true, description = "1-based start line of the selection.")
    int startLine;
    @Option(names = "--start-column", required = true, description = "1-based start column of the selection.")
    int startColumn;
    @Option(names = "--end-line",     required = true, description = "1-based end line of the selection.")
    int endLine;
    @Option(names = "--end-column",   required = true, description = "1-based end column of the selection (exclusive).")
    int endColumn;

    private Path absFile;

    @Override
    protected int validate() {
        absFile = checkedFile(file);
        return absFile == null ? 1 : 0;
    }

    /** Applies the refactoring and returns the modified source. */
    protected abstract String transform(SourceUnit unit, int selStart, int selLen) throws Exception;

    /** One-line success message printed after writing. */
    protected abstract String successMessage(Path absFile);

    @Override
    protected List<FileChange> refactor() throws Exception {
        String source = Files.readString(absFile);
        int selStart  = JdtRenamer.toOffset(source, startLine, startColumn);
        int selLen    = JdtRenamer.toOffset(source, endLine, endColumn) - selStart;
        String result = transform(new SourceUnit(source, absFile.getFileName().toString()), selStart, selLen);
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