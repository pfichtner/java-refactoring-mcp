package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
            RenameStoryBoard.titled("Inline method: greet() — void, no parameters")
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
            RenameStoryBoard.titled("Inline method: printSum(a, b) — void, parameters substituted")
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
            RenameStoryBoard.titled("Inline method: add(x, y) → return expression substituted")
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
            RenameStoryBoard.titled("Inline method: add (multi-file, all call sites, declaration kept)")
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

        assertTrue(changed.containsKey(calcFile.toAbsolutePath().normalize()),
                "Calculator.java must be changed when removeDeclaration=true");

        Approvals.verify(
            RenameStoryBoard.titled("Inline method: add (multi-file, declaration removed)")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------

    @Test
    void inline_rejected_when_body_has_multiple_statements_in_value_context() throws Exception {
        String source = fixtures.load("inline-method/invalid-multiple-returns/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute(5)");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtInlineMethod.inlineMethod(source, "Foo.java", offset));
        assertTrue(ex.getMessage().contains("exactly one statement"));

        Approvals.verify(
            RenameStoryBoard.titled("Inline method — rejected: multi-statement body in value context")
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

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtInlineMethod.inlineMethod(source, "Foo.java", offset));
        assertTrue(ex.getMessage().contains("not declared in this file"));

        Approvals.verify(
            RenameStoryBoard.titled("Inline method — rejected: method not in this file")
                .javaSection("Input", source)
                .refactoring("inline method", "`println(...)` at " + Fixtures.lineCol(source, offset),
                        "method declared in java.io.PrintStream — only single-file inline supported")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
