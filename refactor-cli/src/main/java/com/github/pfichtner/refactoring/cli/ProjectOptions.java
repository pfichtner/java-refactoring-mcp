package com.github.pfichtner.refactoring.cli;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.project.ExplicitProject;
import com.github.pfichtner.refactoring.project.JavaProject;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import picocli.CommandLine.Option;

/**
 * Picocli mixin that provides project-discovery options shared by all commands.
 *
 * Resolution order:
 * <ol>
 *   <li>Explicit flags ({@code --source-root}, {@code --classpath},
 *       {@code --java-version}) → {@link ExplicitProject}.</li>
 *   <li>{@code --project} supplied → {@link ProjectDetector#detect(Path)} on that root.</li>
 *   <li>Neither → {@link ProjectDetector#detect(Path)} walking up from the source file.</li>
 * </ol>
 */
public class ProjectOptions {

    @Option(names = "--project",
            description = "Project root (Maven or Gradle). Auto-detected from --file if omitted.")
    Path projectRoot;

    @Option(names = "--source-root", arity = "1..*",
            description = "Explicit source root(s). When set, skips build-file detection.")
    List<Path> sourceRoots;

    @Option(names = "--classpath",
            description = "Explicit compile classpath (OS path-separator separated jars).")
    String classpath;

    @Option(names = "--java-version",
            description = "Java language level (default: 21). Used with --source-root.")
    String javaVersion;

    /**
     * Returns the project to use, resolved according to the options provided.
     *
     * @param file the source file being refactored (used for auto-detection walk-up)
     */
    public JavaProject resolve(Path file) {
        if (sourceRoots != null && !sourceRoots.isEmpty()) {
            List<Path> roots = sourceRoots.stream()
                    .map(p -> p.toAbsolutePath().normalize())
                    .toList();
            String version = javaVersion != null ? javaVersion : "21";
            String[] cp = classpath != null
                    ? classpath.split(File.pathSeparator)
                    : new String[0];
            Path root = projectRoot != null
                    ? projectRoot.toAbsolutePath().normalize()
                    : roots.get(0).getParent();
            return new ExplicitProject(root, roots, version, cp);
        }
        Path base = projectRoot != null
                ? projectRoot.toAbsolutePath().normalize()
                : file.toAbsolutePath().normalize();
        return ProjectDetector.detect(base);
    }
}
