package com.github.pfichtner;

import com.github.pfichtner.project.GradleProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Gradle project model and project-context rename.
 */
class GradleProjectTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void gradle_project_reads_source_roots_and_java_version() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple-gradle");
        GradleProject project = new GradleProject(projectRoot);

        assertEquals("21", project.javaVersion());

        var sourceRoots = project.sourceRoots();
        assertEquals(1, sourceRoots.size());
        assertTrue(sourceRoots.get(0).endsWith(Path.of("src/main/java")),
                "Expected src/main/java, got: " + sourceRoots.get(0));
        assertTrue(Files.isDirectory(sourceRoots.get(0)),
                "Source root must exist on disk: " + sourceRoots.get(0));
    }

    @Test
    void rename_local_variable_in_gradle_project() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple-gradle");
        GradleProject project = new GradleProject(projectRoot);

        Path sourceFile = project.sourceRoots().get(0)
                .resolve("com/example/Calculator.java");
        String source = Files.readString(sourceFile);
        int offset = Fixtures.offsetOf(source, "int result") + "int ".length();

        String result = JdtRenamer.renameLocalVariable(project, sourceFile, offset, "sum");

        Approvals.verify(
            RenameStoryBoard.titled("Rename local variable in Gradle project: result → sum")
                .javaSection("Input", source)
                .refactoring("rename local variable", "`result` → `sum`",
                        "target: " + Fixtures.lineCol(source, offset)
                        + " in Calculator.java (Gradle project context)")
                .javaSection("Output", result)
                .build()
        );
    }
}
