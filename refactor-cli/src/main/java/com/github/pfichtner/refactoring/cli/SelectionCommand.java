package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.github.pfichtner.refactoring.JdtRenamer;
import com.github.pfichtner.refactoring.SourceUnit;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Shared skeleton for single-file, selection-range refactoring commands (start/end line+column). */
abstract class SelectionCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

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

    @Option(names = "--dry-run", description = "Print result without writing to disk.")
    boolean dryRun;

    /** Applies the refactoring and returns the modified source. */
    protected abstract String transform(SourceUnit unit, int selStart, int selLen) throws Exception;

    /** One-line success message printed after writing. */
    protected abstract String successMessage(Path absFile);

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source = Files.readString(absFile);
        int selStart  = JdtRenamer.toOffset(source, startLine, startColumn);
        int selLen    = JdtRenamer.toOffset(source, endLine, endColumn) - selStart;
        String result = transform(new SourceUnit(source, absFile.getFileName().toString()), selStart, selLen);
        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no file written.");
            out.println();
            out.println(result);
        } else {
            Files.writeString(absFile, result);
            out.println(successMessage(absFile));
        }
        return 0;
    }
}
