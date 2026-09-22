package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.pfichtner.refactoring.project.GradleProject;
import com.github.pfichtner.refactoring.project.MavenProject;
import com.github.pfichtner.refactoring.project.ProjectDetector;
import com.github.pfichtner.refactoring.support.Fixtures;

/**
 * Tests for ProjectDetector walk-up detection.
 */
class ProjectDetectorTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void detects_maven_for_pom_xml() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple");
        assertThat(ProjectDetector.detect(projectRoot)).isInstanceOf(MavenProject.class);
    }

    @Test
    void detects_gradle_for_build_gradle() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple-gradle");
        assertThat(ProjectDetector.detect(projectRoot)).isInstanceOf(GradleProject.class);
    }

    @Test
    void detects_from_file_inside_project(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Path nested = Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        Path javaFile = Files.writeString(nested.resolve("Foo.java"), "class Foo {}");

        assertThat(ProjectDetector.detect(javaFile)).isInstanceOf(MavenProject.class);
    }

    @Test
    void detects_gradle_walking_up_from_nested_file(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");
        Path nested = Files.createDirectories(tempDir.resolve("src/main/java"));
        Path javaFile = Files.writeString(nested.resolve("Foo.java"), "class Foo {}");

        assertThat(ProjectDetector.detect(javaFile)).isInstanceOf(GradleProject.class);
    }

    @Test
    void detects_gradle_kts(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins { java }");
        assertThat(ProjectDetector.detect(tempDir)).isInstanceOf(GradleProject.class);
    }

    @Test
    void prefers_maven_when_both_pom_and_gradle_present(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'java' }");
        assertThat(ProjectDetector.detect(tempDir)).isInstanceOf(MavenProject.class);
    }

    @Test
    void throws_when_no_build_file_found(@TempDir Path tempDir) {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> ProjectDetector.detect(tempDir));
    }
}
