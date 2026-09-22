package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

/**
 * Approval tests for Inline Variable.
 * Each .approved.md shows: input → target variable → output.
 */
class InlineVariableTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void inline_simple_variable() throws Exception {
        String source = fixtures.load("inline-var/simple/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "int x = 6 * 7") + "int ".length();

        String result = JdtInliner.inlineVariable(source, "Foo.java", offset);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline variable: x (initializer is a compound expression)")
                .javaSection("Input", source)
                .refactoring("inline variable", "`x` = `6 * 7`",
                        "declaration at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_adds_parens_to_preserve_precedence() throws Exception {
        String source = fixtures.load("inline-var/with-parens/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "int sum = a + b") + "int ".length();

        String result = JdtInliner.inlineVariable(source, "Foo.java", offset);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline variable: sum — parentheses added to preserve precedence")
                .javaSection("Input", source)
                .refactoring("inline variable", "`sum` = `a + b` (used in `sum * 2`)",
                        "declaration at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_replaces_all_uses() throws Exception {
        String source = fixtures.load("inline-var/multiple-uses/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "String msg") + "String ".length();

        String result = JdtInliner.inlineVariable(source, "Foo.java", offset);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline variable: msg — replaces all two uses")
                .javaSection("Input", source)
                .refactoring("inline variable", "`msg` = `\"Hello\"` (2 uses)",
                        "declaration at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_rejected_when_no_initializer() throws Exception {
        String source = fixtures.load("inline-var/invalid-no-init/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "int x;") + "int ".length();

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtInliner.inlineVariable(source, "Foo.java", offset)).actual();
        assertThat(ex.getMessage()).contains("no initializer");

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline variable — rejected: no initializer")
                .javaSection("Input", source)
                .refactoring("inline variable", "`x` (no initializer)",
                        "declaration at " + Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void inline_rejected_when_multiple_fragments() throws Exception {
        String source = fixtures.load("inline-var/invalid-multiple-fragments/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "int x = 1") + "int ".length();

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtInliner.inlineVariable(source, "Foo.java", offset)).actual();
        assertThat(ex.getMessage()).contains("multiple variables");

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline variable — rejected: multiple fragments in declaration")
                .javaSection("Input", source)
                .refactoring("inline variable", "`x` from `int x = 1, y = 2`",
                        "declaration at " + Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
