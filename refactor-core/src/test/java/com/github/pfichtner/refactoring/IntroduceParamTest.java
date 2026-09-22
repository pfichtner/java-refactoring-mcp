package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.project.MavenProject;
import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

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
            RefactoringStoryBoard.titled("Introduce parameter: \"World\" → String name (multi-file)")
                .inputProject(inputs)
                .refactoring("introduce parameter",
                        "`\"World\"` → parameter `String name`",
                        "in Greeter.greet(); call sites updated with original argument")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Method references: bound and unbound converted to lambdas
    // -------------------------------------------------------------------------

    @Test
    void introduce_param_converts_method_references_to_lambdas() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/introduce-param-method-ref");
        MavenProject project = new MavenProject(projectRoot);

        Path greeterFile = project.sourceRoots().get(0)
                .resolve("com/example/Greeter.java");
        String source = Files.readString(greeterFile);

        int start = source.lastIndexOf("\"World\"");
        int len   = "\"World\"".length();

        Map<Path, String> changed = JdtIntroduceParam.introduceParam(
                project, greeterFile, start, len, "name", "String");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/introduce-param-method-ref/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce parameter: method references converted to lambdas")
                .inputProject(inputs)
                .refactoring("introduce parameter",
                        "`\"World\"` → parameter `String name`",
                        "in Greeter.greet(); method references updated to lambda form")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // FQN type: type name inferred as fully-qualified when not imported
    // -------------------------------------------------------------------------

    @Test
    void introduce_param_infers_fqn_type_when_not_imported() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/introduce-param-fqn");
        MavenProject project = new MavenProject(projectRoot);

        Path appFile = project.sourceRoots().get(0).resolve("com/example/app/App.java");
        String source = Files.readString(appFile);

        // Select the ClassInstanceCreation expression; type must be auto-detected via FQN
        int start = source.indexOf("new com.example.service.Calculator()");
        int len   = "new com.example.service.Calculator()".length();

        Map<Path, String> changed = JdtIntroduceParam.introduceParam(
                project, appFile, start, len, "calc", null);

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/introduce-param-fqn/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce parameter: FQN type inferred when Calculator not imported")
                .inputProject(inputs)
                .refactoring("introduce parameter",
                        "`new com.example.service.Calculator()` → parameter `calc`",
                        Fixtures.lineCol(source, start)
                        + " — type must be emitted as FQN (no import present)")
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

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtIntroduceParam.introduceParam(
                project, greeterFile, start, len, "printer", null)).actual();
        assertThat(ex.getMessage()).contains("simple name");

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce parameter — rejected: selection is a simple name")
                .javaSection("Input", source)
                .refactoring("introduce parameter",
                        "`println` → `printer` (simple name — choose a compound expression)",
                        Fixtures.lineCol(source, start))
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Javadoc: @param tag inserted
    // -------------------------------------------------------------------------

    @Test
    void introduce_param_adds_param_tag_when_javadoc_exists() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/introduce-param-javadoc");
        MavenProject project = new MavenProject(projectRoot);

        Path calcFile = project.sourceRoots().get(0).resolve("com/example/Calculator.java");
        String source = Files.readString(calcFile);

        // Select the literal "a + b" — promote it to a new parameter
        int start = Fixtures.offsetOf(source, "a + b");
        int len   = "a + b".length();

        Map<Path, String> changed = JdtIntroduceParam.introduceParam(
                project, calcFile, start, len, "value", "int");

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce parameter: @param tag inserted after last @param")
                .javaSection("Input", source)
                .refactoring("introduce parameter",
                        "`a + b` → parameter `int value`",
                        Fixtures.lineCol(source, start) + " — @param value must be added to Javadoc")
                .outputProject(changed)
                .build()
        );
    }
}
