package com.github.pfichtner;

import com.github.pfichtner.project.GradleProject;
import com.github.pfichtner.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Gradle project model and project-context rename.
 */
class GradleProjectTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void gradle_project_reads_source_roots_and_java_version() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple-gradle");
        GradleProject project = new GradleProject(projectRoot);

        assertThat(project.javaVersion()).isEqualTo("21");

        var sourceRoots = project.sourceRoots();
        assertThat(sourceRoots.size()).isEqualTo(1);
        assertThat(sourceRoots.get(0).endsWith(Path.of("src/main/java"))).as("Expected src/main/java, got: " + sourceRoots.get(0)).isTrue();
        assertThat(Files.isDirectory(sourceRoots.get(0))).as("Source root must exist on disk: " + sourceRoots.get(0)).isTrue();
    }

    @Test
    void rename_local_variable_in_gradle_project() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple-gradle");
        GradleProject project = new GradleProject(projectRoot);

        Path sourceFile = project.sourceRoots().get(0)
                .resolve("com/example/Calculator.java");
        String source = Files.readString(sourceFile);
        int offset = Fixtures.offsetOf(source, "int result") + "int ".length();

        String withProject    = JdtRenamer.renameLocalVariable(project, sourceFile, offset, "sum");
        String withoutProject = JdtRenamer.renameLocalVariable(source, "Calculator.java", offset, "sum");

        assertThat(withProject).isEqualTo(withoutProject);
    }
}
