package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

/**
 * Approval tests for Extract Constant.
 * Each .approved.md shows: input → selected expression + constant name → output.
 */
class ExtractConstantTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void extract_numeric_literal_as_constant() throws Exception {
        String source = fixtures.load("extract-const/simple/input/Foo.java");
        int start = Fixtures.offsetOf(source, "3.14159");
        int len   = "3.14159".length();

        String result = JdtExtractConstant.extractConstant(
                new SourceUnit(source, "Foo.java"), start, len, "PI", false);

        Approvals.verify(
            RefactoringStoryBoard.titled("Extract constant: 3.14159 → PI")
                .javaSection("Input", source)
                .refactoring("extract constant", "`3.14159` → `private static final double PI`",
                        Fixtures.lineCol(source, start))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void extract_string_literal_replaces_all_occurrences() throws Exception {
        String source = fixtures.load("extract-const/replaces-all/input/Foo.java");
        int start = Fixtures.offsetOf(source, "\"HELLO\"");
        int len   = "\"HELLO\"".length();

        String result = JdtExtractConstant.extractConstant(
                new SourceUnit(source, "Foo.java"), start, len, "GREETING", true);

        Approvals.verify(
            RefactoringStoryBoard.titled("Extract constant: \"HELLO\" → GREETING (replace all)")
                .javaSection("Input", source)
                .refactoring("extract constant", "`\"HELLO\"` → `private static final String GREETING` (replaceAll=true)",
                        Fixtures.lineCol(source, start))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void extract_string_literal_without_replace_all_keeps_other_occurrences() throws Exception {
        String source = fixtures.load("extract-const/replaces-all/input/Foo.java");
        int start = Fixtures.offsetOf(source, "\"HELLO\"");
        int len = "\"HELLO\"".length();

        String result = JdtExtractConstant.extractConstant(
                new SourceUnit(source, "Foo.java"), start, len, "GREETING", false);

        Approvals.verify(
                RefactoringStoryBoard.titled("Extract constant: \"HELLO\" → GREETING (selected occurrence only)")
                        .javaSection("Input", source)
                        .refactoring("extract constant", "`\"HELLO\"` → `private static final String GREETING` (replaceAll=false)",
                                Fixtures.lineCol(source, start))
                        .javaSection("Output", result)
                        .build()
        );
    }

    @Test
    void extract_constant_rejected_when_selection_is_simple_name() throws Exception {
        String source = fixtures.load("extract-const/invalid-simple-name/input/Foo.java");
        int start = Fixtures.offsetOf(source, "int x") + "int ".length();
        int len   = "x".length();

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtExtractConstant.extractConstant(
                new SourceUnit(source, "Foo.java"), start, len, "X", false)).actual();
        assertThat(ex.getMessage()).contains("simple name");

        Approvals.verify(
            RefactoringStoryBoard.titled("Extract constant — rejected: selection is a simple name")
                .javaSection("Input", source)
                .refactoring("extract constant", "`x` → `X` (simple name — nothing to extract)",
                        Fixtures.lineCol(source, start))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
