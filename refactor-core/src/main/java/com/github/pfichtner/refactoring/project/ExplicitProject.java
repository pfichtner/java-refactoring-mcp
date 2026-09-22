package com.github.pfichtner.refactoring.project;

import java.nio.file.Path;
import java.util.List;

/**
 * A {@link JavaProject} whose source roots, Java version, and classpath are
 * supplied directly by the caller — no build-file parsing or tool invocation.
 */
public record ExplicitProject(
        Path root,
        List<Path> sourceRoots,
        String javaVersion,
        String[] classpath
) implements JavaProject {

    public ExplicitProject {
        root = root.toAbsolutePath();
        sourceRoots = List.copyOf(sourceRoots);
    }
}
