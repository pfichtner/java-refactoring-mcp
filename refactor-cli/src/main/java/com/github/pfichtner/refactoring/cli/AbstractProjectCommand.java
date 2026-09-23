package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.github.pfichtner.refactoring.FileChange;

import picocli.CommandLine.Mixin;

/**
 * Base for project-wide refactorings that resolve a {@link com.github.pfichtner.refactoring.project.JavaProject}
 * from the shared {@link ProjectOptions} mixin. Results expressed as a path→source map are
 * adapted to {@link FileChange} via {@link #toChanges(Map)}.
 */
abstract class AbstractProjectCommand extends AbstractRefactoringCommand {

    @Mixin ProjectOptions project;

    protected static List<FileChange> toChanges(Map<Path, String> changed) {
        return changed.entrySet().stream()
                .map(e -> new FileChange(e.getKey(), e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(fc -> fc.newPath().toString()))
                .toList();
    }
}