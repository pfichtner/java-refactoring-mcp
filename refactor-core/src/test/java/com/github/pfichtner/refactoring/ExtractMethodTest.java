package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

/**
 * Approval tests for Extract Method.
 * Each .approved.md shows: input → selection + method name → output.
 */
class ExtractMethodTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void extract_simple_no_params_no_return() throws Exception {
        String source = fixtures.load("extract/simple/input/Greeter.java");
        // Select the two println statements
        int start = Fixtures.offsetOf(source, "System.out.println(\"Hello\")");
        int end   = Fixtures.offsetOf(source, "System.out.println(\"World\")")
                    + "System.out.println(\"World\");".length();

        String result = JdtExtractor.extractMethod(source, "Greeter.java",
                start, end - start, "greet");

        Approvals.verify(
            RefactoringStoryBoard.titled("Extract method: greet() — no params, no return")
                .javaSection("Input", source)
                .refactoring("extract method", "selection → `greet()`",
                        Fixtures.lineCol(source, start) + " to " + Fixtures.lineCol(source, end - 1))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void extract_with_parameter() throws Exception {
        String source = fixtures.load("extract/with-param/input/Computation.java");
        // Select "int result = n * 2;" and "System.out.println(result);"
        int start = Fixtures.offsetOf(source, "int result = n * 2;");
        int end   = Fixtures.offsetOf(source, "System.out.println(result);")
                    + "System.out.println(result);".length();

        String result = JdtExtractor.extractMethod(source, "Computation.java",
                start, end - start, "compute");

        Approvals.verify(
            RefactoringStoryBoard.titled("Extract method: compute(n) — with parameter")
                .javaSection("Input", source)
                .refactoring("extract method", "selection → `compute(int n)`",
                        Fixtures.lineCol(source, start) + " to " + Fixtures.lineCol(source, end - 1))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void extract_with_return_value() throws Exception {
        String source = fixtures.load("extract/with-return/input/Computation.java");
        // Select "int sum = a + b;"
        int start = Fixtures.offsetOf(source, "int sum = a + b;");
        int end   = start + "int sum = a + b;".length();

        String result = JdtExtractor.extractMethod(source, "Computation.java",
                start, end - start, "add");

        Approvals.verify(
            RefactoringStoryBoard.titled("Extract method: add(a, b) — with return value")
                .javaSection("Input", source)
                .refactoring("extract method", "selection → `int add(int a, int b)`",
                        Fixtures.lineCol(source, start))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void extract_rejected_when_selection_contains_return() throws Exception {
        String source = fixtures.load("extract/invalid-return/input/Computation.java");
        // Select the complete if statement (from "if" to its closing "}")
        int start = Fixtures.offsetOf(source, "if (n < 0)");
        // Find the "}" that closes the if block — it appears before "return n * 2"
        int end = source.indexOf("}\n        return n * 2;") + "}".length();

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                JdtExtractor.extractMethod(source, "Computation.java",
                        start, end - start, "validate")).actual();

        Approvals.verify(
            RefactoringStoryBoard.titled("Extract method — rejected: selection contains return")
                .javaSection("Input", source)
                .refactoring("extract method", "selection containing `return -1;` → `validate()`",
                        Fixtures.lineCol(source, start))
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void extract_rejected_when_multiple_vars_used_after() throws Exception {
        String source = fixtures.load("extract/invalid-multiple-outputs/input/Computation.java");
        // Select "int a = 3;" and "int b = 4;"
        int start = Fixtures.offsetOf(source, "int a = 3;");
        int end   = Fixtures.offsetOf(source, "int b = 4;") + "int b = 4;".length();

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
                JdtExtractor.extractMethod(source, "Computation.java",
                        start, end - start, "init")).actual();

        Approvals.verify(
            RefactoringStoryBoard.titled("Extract method — rejected: multiple variables used after selection")
                .javaSection("Input", source)
                .refactoring("extract method", "selection → `init()` (would need to return a and b)",
                        Fixtures.lineCol(source, start))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
