package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtRenamer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * CLI subcommand for rename refactoring. Logic lives in {@link JdtRenamer}.
 */
@Command(
    name = "rename",
    mixinStandardHelpOptions = true,
    description = "Rename the Java symbol at the given file location."
)
public class RenameCommand extends AbstractProjectCommand {

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the symbol to rename.")
    Path file;

    @Mixin LocatorOptions locator;

    @Option(names = {"--name", "-n"}, required = true,
            description = "New name for the symbol.")
    String newName;

    private Path absFile;

    @Override
    protected int validate() {
        absFile = checkedFile(file);
        return absFile == null ? 1 : 0;
    }

    @Override
    protected List<FileChange> refactor() throws Exception {
        String source = Files.readString(absFile);
        int offset    = locator.resolveOffset(source, absFile.getFileName().toString());
        return JdtRenamer.rename(project.resolve(absFile), absFile, offset, newName);
    }

    @Override protected String successLine(int count) { return "Renamed in " + count + " file(s)"; }
}