package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Approval tests for Remove Parameter.
 */
class RemoveParamTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void remove_unused_trailing_parameter_across_files() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/remove-param");
        MavenProject project = new MavenProject(projectRoot);

        Path computationFile = project.sourceRoots().get(0)
                .resolve("com/example/Computation.java");
        String source = Files.readString(computationFile);

        // Remove parameter 'c' (unused in body, at index 2)
        int offset = Fixtures.offsetOf(source, "int c)") + "int ".length();

        Map<Path, String> changed = JdtRemoveParam.removeParam(project, computationFile, offset);

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/remove-param/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Remove parameter: c (unused, index 2) — multi-file")
                .inputProject(inputs)
                .refactoring("remove parameter", "`int c` at index 2 of `add(int a, int b, int c)`",
                        Fixtures.lineCol(source, offset) + " in Computation.java — c not used in body")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void remove_param_converts_method_references_to_lambdas() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/remove-param-method-ref");
        MavenProject project = new MavenProject(projectRoot);

        Path computationFile = project.sourceRoots().get(0)
                .resolve("com/example/Computation.java");
        String source = Files.readString(computationFile);

        // Remove parameter 'b' (unused in body, at index 1)
        int offset = Fixtures.offsetOf(source, "int b)") + "int ".length();

        Map<Path, String> changed = JdtRemoveParam.removeParam(project, computationFile, offset);

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/remove-param-method-ref/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Remove parameter: method references converted to lambdas")
                .inputProject(inputs)
                .refactoring("remove parameter", "`int b` at index 1 of `add(int a, int b)`",
                        Fixtures.lineCol(source, offset) + " in Computation.java — b not used in body")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void remove_param_rejected_when_used_in_body() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/remove-param");
        MavenProject project = new MavenProject(projectRoot);

        Path computationFile = project.sourceRoots().get(0)
                .resolve("com/example/Computation.java");
        String source = Files.readString(computationFile);

        // Try to remove 'a' — it IS used in the body (return a + b)
        // Use "(int a" to avoid matching "int add" (same "int a" substring)
        int offset = Fixtures.offsetOf(source, "(int a") + "(int ".length();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtRemoveParam.removeParam(project, computationFile, offset));
        assertTrue(ex.getMessage().contains("referenced in the method body"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Remove parameter — rejected: parameter used in body")
                .javaSection("Input", source)
                .refactoring("remove parameter", "`int a` at " + Fixtures.lineCol(source, offset),
                        "a is used in the method body (return a + b)")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
