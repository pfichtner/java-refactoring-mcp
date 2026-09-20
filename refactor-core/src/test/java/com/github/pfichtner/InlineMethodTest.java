package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RefactoringStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Approval tests for Inline Method.
 * Each .approved.md shows: input → call site offset → output.
 */
class InlineMethodTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void inline_void_method_no_params() throws Exception {
        String source = fixtures.load("inline-method/void-no-params/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "greet()");

        String result = JdtInlineMethod.inlineMethod(source, "Foo.java", offset);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method: greet() — void, no parameters")
                .javaSection("Input", source)
                .refactoring("inline method", "`greet()` at " + Fixtures.lineCol(source, offset),
                        "void method — body statements replace the call")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_void_method_with_params() throws Exception {
        String source = fixtures.load("inline-method/void-with-params/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "printSum(a, b)");

        String result = JdtInlineMethod.inlineMethod(source, "Foo.java", offset);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method: printSum(a, b) — void, parameters substituted")
                .javaSection("Input", source)
                .refactoring("inline method", "`printSum(a, b)` at " + Fixtures.lineCol(source, offset),
                        "parameters x→a, y→b substituted in body")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_method_that_returns_expression() throws Exception {
        String source = fixtures.load("inline-method/returns-expression/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "add(x, y)");

        String result = JdtInlineMethod.inlineMethod(source, "Foo.java", offset);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method: add(x, y) → return expression substituted")
                .javaSection("Input", source)
                .refactoring("inline method", "`add(x, y)` at " + Fixtures.lineCol(source, offset),
                        "return expression `a + b` with a→x, b→y replaces call")
                .javaSection("Output", result)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Multi-file inline (project-based)
    // -------------------------------------------------------------------------

    @Test
    void inline_method_across_files_inlines_all_call_sites() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-method");
        MavenProject project = new MavenProject(projectRoot);

        Path appFile  = project.sourceRoots().get(0).resolve("com/example/App.java");
        Path calcFile = project.sourceRoots().get(0).resolve("com/example/Calculator.java");
        String appSource  = Files.readString(appFile);
        String calcSource = Files.readString(calcFile);

        // Offset of 'a' in ".add(" in App.java
        int offset = Fixtures.offsetOf(appSource, ".add(") + 1;

        Map<Path, String> changed = JdtInlineMethod.inlineMethod(project, appFile, offset, false);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method: add (multi-file, all call sites, declaration kept)")
                .inputProject(Map.of("App.java", appSource, "Calculator.java", calcSource))
                .refactoring("inline method",
                        "`Calculator.add(int a, int b)` → inlined at all call sites",
                        "call site in App.java replaced with expression; Calculator.java unchanged")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void inline_method_across_files_removes_declaration_when_requested() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-method");
        MavenProject project = new MavenProject(projectRoot);

        Path appFile  = project.sourceRoots().get(0).resolve("com/example/App.java");
        Path calcFile = project.sourceRoots().get(0).resolve("com/example/Calculator.java");
        String appSource = Files.readString(appFile);

        int offset = Fixtures.offsetOf(appSource, ".add(") + 1;
        Map<Path, String> changed = JdtInlineMethod.inlineMethod(project, appFile, offset, true);

        assertThat(changed.containsKey(calcFile.toAbsolutePath().normalize())).as("Calculator.java must be changed when removeDeclaration=true").isTrue();

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method: add (multi-file, declaration removed)")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Single-file: all occurrences
    // -------------------------------------------------------------------------

    @Test
    void inline_all_occurrences_void_method_keeps_declaration() throws Exception {
        String source = fixtures.load("inline-method/all-occurrences-void/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "greet()");

        String result = JdtInlineMethod.inlineMethod(source, "Foo.java", offset, true, false);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method: greet() — all occurrences, declaration kept")
                .javaSection("Input", source)
                .refactoring("inline method", "`greet()` — all occurrences in file, declaration kept",
                        "both call sites expanded in-place; greet() declaration stays")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_all_occurrences_returns_expression_keeps_declaration() throws Exception {
        String source = fixtures.load("inline-method/all-occurrences-returns/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "add(x, y)");

        String result = JdtInlineMethod.inlineMethod(source, "Foo.java", offset, true, false);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method: add() — all occurrences, declaration kept")
                .javaSection("Input", source)
                .refactoring("inline method", "`add(...)` — all occurrences in file, declaration kept",
                        "both call sites replaced with return expression; add() declaration stays")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_all_occurrences_removes_declaration() throws Exception {
        String source = fixtures.load("inline-method/all-occurrences-remove-decl/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "greet()");

        String result = JdtInlineMethod.inlineMethod(source, "Foo.java", offset, true, true);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method: greet() — all occurrences, declaration removed")
                .javaSection("Input", source)
                .refactoring("inline method", "`greet()` — all occurrences, declaration removed",
                        "both call sites expanded; greet() declaration deleted")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_rejected_when_remove_declaration_without_all_occurrences() throws Exception {
        String source = fixtures.load("inline-method/void-no-params/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "greet()");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtInlineMethod.inlineMethod(source, "Foo.java", offset, false, true)).actual();
        assertThat(ex.getMessage()).contains("allOccurrences=true");

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method — rejected: removeDeclaration without allOccurrences")
                .javaSection("Input", source)
                .refactoring("inline method", "`greet()` — allOccurrences=false, removeDeclaration=true",
                        "declaration removal requires all occurrences to be inlined")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    // -------------------------------------------------------------------------

    @Test
    void inline_rejected_when_body_has_multiple_statements_in_value_context() throws Exception {
        String source = fixtures.load("inline-method/invalid-multiple-returns/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute(5)");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtInlineMethod.inlineMethod(source, "Foo.java", offset)).actual();
        assertThat(ex.getMessage()).contains("exactly one statement");

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method — rejected: multi-statement body in value context")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute(5)` at " + Fixtures.lineCol(source, offset),
                        "method has 2 statements — cannot inline into an expression context")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void inline_rejected_when_method_not_in_file() throws Exception {
        String source = fixtures.load("inline-method/invalid-not-in-file/input/Foo.java");
        // Target System.out.println — defined in java.io.PrintStream, not in this file
        int offset = Fixtures.offsetOf(source, "println");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtInlineMethod.inlineMethod(source, "Foo.java", offset)).actual();
        assertThat(ex.getMessage()).contains("not declared in this file");

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline method — rejected: method not in this file")
                .javaSection("Input", source)
                .refactoring("inline method", "`println(...)` at " + Fixtures.lineCol(source, offset),
                        "method declared in java.io.PrintStream — only single-file inline supported")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
