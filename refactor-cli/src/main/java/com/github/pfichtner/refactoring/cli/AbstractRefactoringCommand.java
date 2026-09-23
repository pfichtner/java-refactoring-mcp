package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import com.github.pfichtner.refactoring.FileChange;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Single common superclass for all CLI subcommands. Owns the dry-run/apply
 * dispatch, the file-not-found check and the change-writing IO; subclasses only
 * declare their options, implement {@link #refactor()} and — where the wording
 * differs — override one of the reporting hooks.
 */
abstract class AbstractRefactoringCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = "--dry-run",
            description = "Print changed sources; do not write to disk.")
    boolean dryRun;

    @Override
    public final Integer call() throws Exception {
        if (validate() != 0) {
            return 1;
        }
        List<FileChange> changes = refactor();
        if (dryRun) {
            printDryRun(changes);
        } else {
            writeChanges(changes);
            printApplyReport(changes);
        }
        return 0;
    }

    /** Returns a non-zero exit code to abort, zero to continue. */
    protected int validate() {
        return 0;
    }

    /** Applies the refactoring and returns every file to create or overwrite. */
    protected abstract List<FileChange> refactor() throws Exception;

    /** Normalizes and checks {@code file}; on failure prints to stderr and returns {@code null}. */
    protected Path checkedFile(Path file) {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return null;
        }
        return absFile;
    }

    /** Writes all changes, creating parent dirs and deleting moved-away originals. */
    protected static void writeChanges(List<FileChange> changes) throws Exception {
        for (FileChange fc : changes) {
            Files.createDirectories(fc.newPath().getParent());
            Files.writeString(fc.newPath(), fc.newSource());
            if (fc.pathChanged()) {
                Files.deleteIfExists(fc.oldPath());
            }
        }
    }

    // -------------------------------------------------------------------
    // Default (canonical) reporting for project-wide file changes
    // -------------------------------------------------------------------

    protected void printDryRun(List<FileChange> changes) {
        var out = spec.commandLine().getOut();
        out.println("Dry run — no files written.");
        out.println("Would change (" + changes.size() + "):");
        changes.stream()
                .sorted(Comparator.comparing(fc -> fc.newPath().toString()))
                .forEach(fc -> {
                    out.println();
                    out.println("=== " + fc.newPath().getFileName() + " ===");
                    out.println(fc.newSource().stripTrailing());
                });
    }

    protected void printApplyReport(List<FileChange> changes) {
        var out = spec.commandLine().getOut();
        out.println(successLine(changes.size()));
        changes.stream()
                .sorted(Comparator.comparing(fc -> fc.newPath().toString()))
                .forEach(fc -> {
                    if (fc.pathChanged()) {
                        out.println("  " + fc.oldPath().getFileName() + " → " + fc.newPath());
                    } else {
                        out.println("  " + fc.newPath().getFileName());
                    }
                });
    }

    /** One-line summary printed after writing (default for the canonical report). */
    protected String successLine(int count) {
        return "Changed " + count + " file(s)";
    }
}