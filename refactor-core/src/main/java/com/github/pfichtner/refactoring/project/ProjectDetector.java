package com.github.pfichtner.refactoring.project;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Detects the build system for a Java source file by walking up the directory
 * tree and returning an appropriate {@link JavaProject}.
 *
 * Detection order (first match wins):
 * <ol>
 *   <li>{@code pom.xml} present → {@link MavenProject}</li>
 *   <li>{@code build.gradle} or {@code build.gradle.kts} present → {@link GradleProject}</li>
 * </ol>
 */
public final class ProjectDetector {

    private ProjectDetector() {}

    /**
     * Detects the project rooted at {@code fileOrDir} or any of its ancestors.
     *
     * @param fileOrDir a source file or directory inside the project
     * @return the detected {@link JavaProject}
     * @throws IllegalStateException if no supported build file is found
     */
    public static JavaProject detect(Path fileOrDir) {
        Path dir = fileOrDir.toAbsolutePath().normalize();
        if (Files.isRegularFile(dir)) dir = dir.getParent();
        while (dir != null) {
            if (Files.exists(dir.resolve("pom.xml"))) {
                return new MavenProject(dir);
            }
            if (Files.exists(dir.resolve("build.gradle"))
                    || Files.exists(dir.resolve("build.gradle.kts"))) {
                return new GradleProject(dir);
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "No pom.xml or build.gradle found by walking up from: " + fileOrDir);
    }
}
