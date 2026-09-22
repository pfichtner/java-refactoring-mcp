package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Shared skeleton for single-file, single-String-result refactoring commands. */
abstract class SingleFileCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file to refactor.")
    Path file;

    @Option(names = "--dry-run",
            description = "Print result without writing to disk.")
    boolean dryRun;

    /** Resolves the target offset inside {@code source}. Subclasses add the locator options. */
    protected abstract int resolveOffset(String source, String unitName);

    /** Applies the refactoring and returns the modified source. */
    protected abstract String transform(String source, String unitName, int offset) throws Exception;

    /** One-line success message printed after writing (receives the absolute file path). */
    protected abstract String successMessage(Path absFile);

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source = Files.readString(absFile);
        int    offset = resolveOffset(source, absFile.getFileName().toString());
        String result = transform(source, absFile.getFileName().toString(), offset);
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
