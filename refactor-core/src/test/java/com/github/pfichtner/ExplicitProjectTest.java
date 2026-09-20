package com.github.pfichtner;

import com.github.pfichtner.project.ExplicitProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ExplicitProject — project constructed from caller-supplied source roots,
 * Java version, and classpath, with no build-file parsing.
 */
class ExplicitProjectTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void explicit_project_uses_provided_source_roots_and_version() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple");
        Path srcRoot     = projectRoot.resolve("src/main/java");

        ExplicitProject project = new ExplicitProject(projectRoot, List.of(srcRoot), "17", new String[0]);

        assertEquals("17", project.javaVersion());
        assertEquals(List.of(srcRoot.toAbsolutePath()), project.sourceRoots());
        assertEquals(0, project.classpath().length);
        assertTrue(Files.isDirectory(project.sourceRoots().get(0)));
    }

    @Test
    void rename_local_variable_with_explicit_project() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple");
        Path srcRoot     = projectRoot.resolve("src/main/java");
        ExplicitProject project = new ExplicitProject(projectRoot, List.of(srcRoot), "21", new String[0]);

        Path sourceFile = srcRoot.resolve("com/example/Calculator.java");
        String source   = Files.readString(sourceFile);
        int offset      = Fixtures.offsetOf(source, "int result") + "int ".length();

        String result = JdtRenamer.renameLocalVariable(project, sourceFile, offset, "total");

        Approvals.verify(
            RenameStoryBoard.titled("Rename local variable with ExplicitProject: result → total")
                .javaSection("Input", source)
                .refactoring("rename local variable", "`result` → `total`",
                        "target: " + Fixtures.lineCol(source, offset)
                        + " in Calculator.java (ExplicitProject — no build file)")
                .javaSection("Output", result)
                .build()
        );
    }
}
