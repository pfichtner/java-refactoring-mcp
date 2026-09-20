package com.github.pfichtner;

import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RefactoringStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ConvertNestedToTopLevelTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void convert_static_nested_class_to_top_level() throws Exception {
        String source = fixtures.load("convert-nested/static-class/input/Outer.java");
        int offset = Fixtures.offsetOf(source, "static class Helper");

        JdtConvertNestedToTopLevel.Result result =
                JdtConvertNestedToTopLevel.convert(source, "Outer.java", offset);

        Approvals.verify(
                RefactoringStoryBoard.titled("Convert nested class Helper → top-level class Helper.java")
                        .javaSection("Input: Outer.java", source)
                        .refactoring("convert nested to top-level",
                                "`private static class Helper` → new file `Helper.java`",
                                Fixtures.lineCol(source, offset))
                        .javaSection("Output: Outer.java", result.outerSource())
                        .javaSection("New file: " + result.newTypeName() + ".java", result.newTypeSource())
                        .build()
        );
    }

    @Test
    void convert_nested_interface_to_top_level() throws Exception {
        String source = fixtures.load("convert-nested/nested-interface/input/Container.java");
        int offset = Fixtures.offsetOf(source, "interface Transformer");

        JdtConvertNestedToTopLevel.Result result =
                JdtConvertNestedToTopLevel.convert(source, "Container.java", offset);

        Approvals.verify(
                RefactoringStoryBoard.titled("Convert nested interface Transformer → top-level Transformer.java")
                        .javaSection("Input: Container.java", source)
                        .refactoring("convert nested to top-level",
                                "`public interface Transformer` → new file `Transformer.java`",
                                Fixtures.lineCol(source, offset))
                        .javaSection("Output: Container.java", result.outerSource())
                        .javaSection("New file: " + result.newTypeName() + ".java", result.newTypeSource())
                        .build()
        );
    }

    @Test
    void convert_rejected_when_no_nested_type_at_offset() throws Exception {
        String source = fixtures.load("convert-nested/static-class/input/Outer.java");
        int offset = Fixtures.offsetOf(source, "getName");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtConvertNestedToTopLevel.convert(source, "Outer.java", offset))
                .actual();
        assertThat(ex.getMessage()).contains("No nested type");

        Approvals.verify(
                RefactoringStoryBoard.titled("Convert nested rejected: no nested type at offset")
                        .javaSection("Input: Outer.java", source)
                        .refactoring("convert nested to top-level",
                                "offset inside method `getName()`, not a nested type",
                                Fixtures.lineCol(source, offset))
                        .diagnostic(ex.getMessage())
                        .build()
        );
    }
}
