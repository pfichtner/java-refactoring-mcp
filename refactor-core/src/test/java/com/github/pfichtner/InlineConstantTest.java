package com.github.pfichtner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RefactoringStoryBoard;

/**
 * Approval tests for Inline Constant.
 * Each .approved.md shows: input → offset context → output (or diagnostic on rejection).
 */
class InlineConstantTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void inline_constant_this_occurrence() throws Exception {
        String source = fixtures.load("inline-constant/int-constant/input/Foo.java");
        // point to the first USE of MAX (in isValid), not the declaration
        int offset = Fixtures.offsetOf(source, "x <= MAX") + "x <= ".length();

        String result = JdtInliner.inlineConstant(source, "Foo.java", offset, false, false);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline constant: MAX — this occurrence only")
                .javaSection("Input", source)
                .refactoring("inline constant", "`MAX` at " + Fixtures.lineCol(source, offset),
                        "only the first reference replaced; declaration and other uses unchanged")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_constant_all_occurrences_keeps_declaration() throws Exception {
        String source = fixtures.load("inline-constant/int-constant/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "MAX");

        String result = JdtInliner.inlineConstant(source, "Foo.java", offset, true, false);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline constant: MAX — all occurrences, declaration kept")
                .javaSection("Input", source)
                .refactoring("inline constant", "`MAX` — all occurrences, declaration kept",
                        "both references replaced with 100; MAX declaration stays")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_constant_all_occurrences_removes_declaration() throws Exception {
        String source = fixtures.load("inline-constant/int-constant/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "MAX");

        String result = JdtInliner.inlineConstant(source, "Foo.java", offset, true, true);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline constant: MAX — all occurrences, declaration removed")
                .javaSection("Input", source)
                .refactoring("inline constant", "`MAX` — all occurrences, declaration removed",
                        "both references replaced with 100; MAX field declaration deleted")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_constant_with_parens() throws Exception {
        String source = fixtures.load("inline-constant/with-parens/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "FACTOR");

        String result = JdtInliner.inlineConstant(source, "Foo.java", offset, true, false);

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline constant: FACTOR — compound initializer needs parens")
                .javaSection("Input", source)
                .refactoring("inline constant", "`FACTOR` — all occurrences, declaration kept",
                        "InfixExpression initializer wrapped in parens to preserve precedence")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_constant_rejected_remove_without_all() throws Exception {
        String source = fixtures.load("inline-constant/int-constant/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "MAX");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtInliner.inlineConstant(source, "Foo.java", offset, false, true)).actual();
        assertThat(ex.getMessage()).contains("allOccurrences=true");

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline constant — rejected: removeDeclaration without allOccurrences")
                .javaSection("Input", source)
                .refactoring("inline constant", "`MAX` — allOccurrences=false, removeDeclaration=true",
                        "declaration removal requires all occurrences to be inlined")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void inline_constant_rejected_mutable_field() throws Exception {
        String source = fixtures.load("inline-constant/invalid-mutable/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "count");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtInliner.inlineConstant(source, "Foo.java", offset, true, false)).actual();
        assertThat(ex.getMessage()).contains("static final");

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline constant — rejected: mutable field")
                .javaSection("Input", source)
                .refactoring("inline constant", "`count` — not a static final field",
                        "only static final constants can be inlined")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void inline_constant_rejected_multiple_fragments() throws Exception {
        String source = fixtures.load("inline-constant/invalid-multiple-fragments/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "return X") + "return ".length();

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtInliner.inlineConstant(source, "Foo.java", offset, true, false)).actual();
        assertThat(ex.getMessage()).contains("multiple constants");

        Approvals.verify(
            RefactoringStoryBoard.titled("Inline constant — rejected: multiple constants in one declaration")
                .javaSection("Input", source)
                .refactoring("inline constant", "`X` — declaration has multiple fragments",
                        "split the declaration before inlining")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
