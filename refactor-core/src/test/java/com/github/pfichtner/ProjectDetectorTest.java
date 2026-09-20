package com.github.pfichtner;

import com.github.pfichtner.project.GradleProject;
import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.project.ProjectDetector;
import com.github.pfichtner.support.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProjectDetector walk-up detection.
 */
class ProjectDetectorTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void detects_maven_for_pom_xml() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple");
        assertInstanceOf(MavenProject.class, ProjectDetector.detect(projectRoot));
    }

    @Test
    void detects_gradle_for_build_gradle() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple-gradle");
        assertInstanceOf(GradleProject.class, ProjectDetector.detect(projectRoot));
    }

    @Test
    void detects_from_file_inside_project(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Path nested = Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        Path javaFile = Files.writeString(nested.resolve("Foo.java"), "class Foo {}");

        assertInstanceOf(MavenProject.class, ProjectDetector.detect(javaFile));
    }

    @Test
    void detects_gradle_walking_up_from_nested_file(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");
        Path nested = Files.createDirectories(tempDir.resolve("src/main/java"));
        Path javaFile = Files.writeString(nested.resolve("Foo.java"), "class Foo {}");

        assertInstanceOf(GradleProject.class, ProjectDetector.detect(javaFile));
    }

    @Test
    void detects_gradle_kts(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins { java }");
        assertInstanceOf(GradleProject.class, ProjectDetector.detect(tempDir));
    }

    @Test
    void prefers_maven_when_both_pom_and_gradle_present(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");
        assertInstanceOf(MavenProject.class, ProjectDetector.detect(tempDir));
    }

    @Test
    void throws_when_no_build_file_found(@TempDir Path tempDir) {
        assertThrows(IllegalStateException.class, () -> ProjectDetector.detect(tempDir));
    }
}
