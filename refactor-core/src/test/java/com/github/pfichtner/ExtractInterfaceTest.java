package com.github.pfichtner;

import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Approval tests for Extract Interface.
 * Each .approved.md shows: input class → interface name → modified class + new interface.
 */
class ExtractInterfaceTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void extract_all_public_methods_into_interface() throws Exception {
        String source = fixtures.load("extract-interface/simple/input/Calculator.java");

        JdtExtractInterface.Result result =
                JdtExtractInterface.extractInterface(source, "Calculator.java", "Arithmetic", List.of());

        Approvals.verify(
            RenameStoryBoard.titled("Extract interface: Arithmetic from Calculator")
                .javaSection("Input: Calculator.java", source)
                .refactoring("extract interface", "`Calculator` → implements `Arithmetic`",
                        "all public non-static methods: add, subtract (private helper excluded)")
                .javaSection("Output: Calculator.java", result.modifiedClassSource())
                .javaSection("New: Arithmetic.java", result.interfaceSource())
                .build()
        );
    }

    @Test
    void extract_subset_of_methods() throws Exception {
        String source = fixtures.load("extract-interface/simple/input/Calculator.java");

        JdtExtractInterface.Result result = JdtExtractInterface.extractInterface(
                source, "Calculator.java", "Addable", List.of("add"));

        Approvals.verify(
            RenameStoryBoard.titled("Extract interface: Addable from Calculator (subset)")
                .javaSection("Input: Calculator.java", source)
                .refactoring("extract interface", "`Calculator` → implements `Addable`",
                        "selected methods: add only")
                .javaSection("Output: Calculator.java", result.modifiedClassSource())
                .javaSection("New: Addable.java", result.interfaceSource())
                .build()
        );
    }

    @Test
    void extract_interface_with_generic_method() throws Exception {
        String source = fixtures.load("extract-interface/with-generics/input/Converter.java");

        JdtExtractInterface.Result result = JdtExtractInterface.extractInterface(
                source, "Converter.java", "Transformable", List.of());

        Approvals.verify(
            RenameStoryBoard.titled("Extract interface: Transformable from Converter (generics)")
                .javaSection("Input: Converter.java", source)
                .refactoring("extract interface", "`Converter` → implements `Transformable`",
                        "includes generic method <T> T identity(T value)")
                .javaSection("Output: Converter.java", result.modifiedClassSource())
                .javaSection("New: Transformable.java", result.interfaceSource())
                .build()
        );
    }

    @Test
    void extract_rejected_when_no_public_methods() throws Exception {
        String source = fixtures.load("extract-interface/invalid-no-public-methods/input/Foo.java");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtExtractInterface.extractInterface(
                source, "Foo.java", "FooInterface", List.of())).actual();
        assertThat(ex.getMessage()).contains("No public non-static methods");

        Approvals.verify(
            RenameStoryBoard.titled("Extract interface — rejected: no public non-static methods")
                .javaSection("Input: Foo.java", source)
                .refactoring("extract interface", "`Foo` → `FooInterface`",
                        "all methods are private or static — nothing to extract")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
