package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Maven project model and project-context rename.
 */
class MavenProjectTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void maven_project_reads_source_roots_and_java_version() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple");
        MavenProject project = new MavenProject(projectRoot);

        assertThat(project.javaVersion()).isEqualTo("21");

        var sourceRoots = project.sourceRoots();
        assertThat(sourceRoots.size()).isEqualTo(1);
        assertThat(sourceRoots.get(0).endsWith(Path.of("src/main/java"))).as("Expected src/main/java, got: " + sourceRoots.get(0)).isTrue();
        assertThat(Files.isDirectory(sourceRoots.get(0))).as("Source root must exist on disk: " + sourceRoots.get(0)).isTrue();
    }

    @Test
    void rename_local_variable_in_maven_project() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple");
        MavenProject project = new MavenProject(projectRoot);

        Path sourceFile = project.sourceRoots().get(0)
                .resolve("com/example/Calculator.java");
        String source = Files.readString(sourceFile);
        int offset = Fixtures.offsetOf(source, "int result") + "int ".length();

        String result = JdtRenamer.renameLocalVariable(project, sourceFile, offset, "sum");

        Approvals.verify(
            RenameStoryBoard.titled("Rename local variable in Maven project: result → sum")
                .javaSection("Input", source)
                .refactoring("rename local variable", "`result` → `sum`",
                        "target: " + Fixtures.lineCol(source, offset)
                        + " in Calculator.java (Maven project context)")
                .javaSection("Output", result)
                .build()
        );
    }
}
