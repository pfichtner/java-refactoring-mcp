package com.github.pfichtner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RefactoringStoryBoard;

/**
 * Approval tests for Decompose Conditional.
 * Covers if-statement extraction, while-statement extraction, and rejection cases.
 */
class DecomposeConditionalTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    // -------------------------------------------------------------------------
    // if-statement condition extraction
    // -------------------------------------------------------------------------

    @Test
    void decompose_if_condition_into_method() throws Exception {
        String source = fixtures.load("decompose-conditional/basic/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "age >= 18");

        String result = JdtDecomposeConditional.decomposeConditional(source, "Foo.java", offset, "isAdultPremium");

        assertThat(result).contains("isAdultPremium()");
        assertThat(result).contains("return age >= 18 && premium;");

        Approvals.verify(
            RefactoringStoryBoard.titled("Decompose conditional: if (age >= 18 && premium) → isAdultPremium()")
                .javaSection("Input", source)
                .refactoring("decompose conditional",
                        "`if (age >= 18 && premium)` → `if (isAdultPremium())`",
                        "condition extracted into private boolean method")
                .javaSection("Output", result)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // while-statement condition extraction
    // -------------------------------------------------------------------------

    @Test
    void decompose_while_condition_into_method() throws Exception {
        String source = fixtures.load("decompose-conditional/while/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "count < max");

        String result = JdtDecomposeConditional.decomposeConditional(source, "Foo.java", offset, "shouldContinue");

        assertThat(result).contains("shouldContinue()");
        assertThat(result).contains("return count < max && max > 0;");

        Approvals.verify(
            RefactoringStoryBoard.titled("Decompose conditional: while (count < max && max > 0) → shouldContinue()")
                .javaSection("Input", source)
                .refactoring("decompose conditional",
                        "`while (count < max && max > 0)` → `while (shouldContinue())`",
                        "condition extracted into private boolean method")
                .javaSection("Output", result)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Rejection: condition too simple
    // -------------------------------------------------------------------------

    @Test
    void rejected_when_condition_is_simple_name() throws Exception {
        String source = "public class Foo { boolean flag; void m() { if (flag) {} } }";
        int offset = source.indexOf("flag)");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtDecomposeConditional.decomposeConditional(source, "Foo.java", offset, "isReady"))
                .actual();

        assertThat(ex.getMessage()).contains("too simple");

        Approvals.verify(
            RefactoringStoryBoard.titled("Decompose conditional rejected: condition is too simple (single name)")
                .javaSection("Input", source)
                .refactoring("decompose conditional", "`if (flag)` — single name, nothing to decompose", "")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Rejection: method already exists
    // -------------------------------------------------------------------------

    @Test
    void rejected_when_method_already_exists() throws Exception {
        String source = """
                public class Foo {
                    int age;
                    void m() { if (age > 18 && age < 65) {} }
                    private boolean isEligible() { return true; }
                }
                """;
        int offset = source.indexOf("age > 18");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtDecomposeConditional.decomposeConditional(source, "Foo.java", offset, "isEligible"))
                .actual();

        assertThat(ex.getMessage()).contains("isEligible");

        Approvals.verify(
            RefactoringStoryBoard.titled("Decompose conditional rejected: method already exists")
                .javaSection("Input", source)
                .refactoring("decompose conditional", "`if (age > 18 && age < 65)` → `isEligible()` — method name already taken", "")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
