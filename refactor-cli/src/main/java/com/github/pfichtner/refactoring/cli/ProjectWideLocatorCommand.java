package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/** Shared skeleton for project-wide refactorings that locate a single element and return changed files. */
abstract class ProjectWideLocatorCommand extends AbstractProjectCommand {

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the target element.")
    Path file;

    @Mixin LocatorOptions locator;

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
        return toChanges(execute(project.resolve(absFile), absFile, offset));
    }

    protected abstract Map<Path, String> execute(JavaProject proj, Path absFile, int offset) throws Exception;
    @Override protected abstract String successLine(int count);
}