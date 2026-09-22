package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

class PromoteToFieldTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void promote_local_with_initializer_to_field() throws Exception {
        String source = fixtures.load("promote-to-field/with-initializer/input/Calc.java");
        int offset = Fixtures.offsetOf(source, "int result");

        String result = JdtPromoteToField.promote(source, "Calc.java", offset);

        Approvals.verify(
                RefactoringStoryBoard.titled("Promote local variable result → private field")
                        .javaSection("Input", source)
                        .refactoring("promote to field",
                                "`int result = x * 2;` → `private int result;` field + assignment",
                                Fixtures.lineCol(source, offset))
                        .javaSection("Output", result)
                        .build()
        );
    }

    @Test
    void promote_local_without_initializer_to_field() throws Exception {
        String source = fixtures.load("promote-to-field/without-initializer/input/Counter.java");
        int offset = Fixtures.offsetOf(source, "int count");

        String result = JdtPromoteToField.promote(source, "Counter.java", offset);

        Approvals.verify(
                RefactoringStoryBoard.titled("Promote local variable count (no initializer) → private field")
                        .javaSection("Input", source)
                        .refactoring("promote to field",
                                "`int count;` (no initializer) → `private int count;` field, declaration removed",
                                Fixtures.lineCol(source, offset))
                        .javaSection("Output", result)
                        .build()
        );
    }

    @Test
    void promote_rejected_when_no_local_variable_at_offset() throws Exception {
        String source = fixtures.load("promote-to-field/with-initializer/input/Calc.java");
        int offset = Fixtures.offsetOf(source, "return result");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtPromoteToField.promote(source, "Calc.java", offset))
                .actual();
        assertThat(ex.getMessage()).contains("No local variable declaration");

        Approvals.verify(
                RefactoringStoryBoard.titled("Promote to field rejected: no local variable at offset")
                        .javaSection("Input", source)
                        .refactoring("promote to field",
                                "offset inside `return result`, not a local variable declaration",
                                Fixtures.lineCol(source, offset))
                        .diagnostic(ex.getMessage())
                        .build()
        );
    }
}
