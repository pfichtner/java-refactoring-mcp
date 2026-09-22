package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

class ConvertAnonymousToNestedTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void convert_runnable_anonymous_to_nested_class() throws Exception {
        String source = fixtures.load("convert-anonymous/simple/input/Outer.java");
        int offset = Fixtures.offsetOf(source, "new Runnable()");

        String result = JdtConvertAnonymousToNested.convert(source, "Outer.java", offset, "Worker");

        Approvals.verify(
                RefactoringStoryBoard.titled("Convert anonymous Runnable → nested class Worker")
                        .javaSection("Input", source)
                        .refactoring("convert anonymous to nested",
                                "`new Runnable() { ... }` → `new Worker()`; add `private class Worker implements Runnable`",
                                Fixtures.lineCol(source, offset))
                        .javaSection("Output", result)
                        .build()
        );
    }

    @Test
    void convert_comparator_anonymous_to_nested_class() throws Exception {
        String source = fixtures.load("convert-anonymous/no-constructor-args/input/Outer.java");
        int offset = Fixtures.offsetOf(source, "new Comparator<String>()");

        String result = JdtConvertAnonymousToNested.convert(source, "Outer.java", offset, "CaseInsensitiveOrder");

        Approvals.verify(
                RefactoringStoryBoard.titled("Convert anonymous Comparator → nested class CaseInsensitiveOrder")
                        .javaSection("Input", source)
                        .refactoring("convert anonymous to nested",
                                "`new Comparator<String>() { ... }` → `new CaseInsensitiveOrder()`",
                                Fixtures.lineCol(source, offset))
                        .javaSection("Output", result)
                        .build()
        );
    }

    @Test
    void convert_rejected_when_no_anonymous_class_at_offset() throws Exception {
        String source = fixtures.load("convert-anonymous/simple/input/Outer.java");
        int offset = Fixtures.offsetOf(source, "void start()");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtConvertAnonymousToNested.convert(source, "Outer.java", offset, "Worker"))
                .actual();
        assertThat(ex.getMessage()).contains("No anonymous class");

        Approvals.verify(
                RefactoringStoryBoard.titled("Convert anonymous rejected: no anonymous class at offset")
                        .javaSection("Input", source)
                        .refactoring("convert anonymous to nested",
                                "offset points to `void start()`, not an anonymous class",
                                Fixtures.lineCol(source, offset))
                        .diagnostic(ex.getMessage())
                        .build()
        );
    }
}
