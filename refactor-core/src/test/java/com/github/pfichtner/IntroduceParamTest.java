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
 * Approval tests for Introduce Parameter.
 * Each .approved.md shows: input project → selected expression → output (method + call sites).
 */
class IntroduceParamTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    // -------------------------------------------------------------------------
    // Multi-file: expression promoted across call sites
    // -------------------------------------------------------------------------

    @Test
    void introduce_param_updates_call_sites() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/introduce-param");
        MavenProject project = new MavenProject(projectRoot);

        Path greeterFile = project.sourceRoots().get(0)
                .resolve("com/example/Greeter.java");
        String source = Files.readString(greeterFile);

        // Select the second string literal "World" in greet()
        int start = source.lastIndexOf("\"World\"");
        int len   = "\"World\"".length();

        Map<Path, String> changed = JdtIntroduceParam.introduceParam(
                project, greeterFile, start, len, "name", "String");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/introduce-param/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Introduce parameter: \"World\" → String name (multi-file)")
                .inputProject(inputs)
                .refactoring("introduce parameter",
                        "`\"World\"` → parameter `String name`",
                        "in Greeter.greet(); call sites updated with original argument")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Precondition: simple name rejected
    // -------------------------------------------------------------------------

    @Test
    void introduce_param_rejected_when_selection_is_simple_name() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/introduce-param");
        MavenProject project = new MavenProject(projectRoot);

        Path greeterFile = project.sourceRoots().get(0)
                .resolve("com/example/Greeter.java");
        String source = Files.readString(greeterFile);

        // `println` in System.out.println(...) is a SimpleName — selecting it is rejected
        int start = Fixtures.offsetOf(source, "println");
        int len   = "println".length();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtIntroduceParam.introduceParam(
                        project, greeterFile, start, len, "printer", null));
        assertTrue(ex.getMessage().contains("simple name"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Introduce parameter — rejected: selection is a simple name")
                .javaSection("Input", source)
                .refactoring("introduce parameter",
                        "`println` → `printer` (simple name — choose a compound expression)",
                        Fixtures.lineCol(source, start))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
