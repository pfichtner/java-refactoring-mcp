package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

class IntroduceIndirectionTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void introduce_indirection_for_instance_method() throws Exception {
        String source = fixtures.load("introduce-indirection/instance-method/input/Service.java");
        int offset = Fixtures.offsetOf(source, "process");

        String result = JdtIntroduceIndirection.introduceIndirection(
                source, "Service.java", offset, "doProcess");

        Approvals.verify(
                RefactoringStoryBoard.titled("Introduce indirection: Service.process → static doProcess(Service, String)")
                        .javaSection("Input", source)
                        .refactoring("introduce indirection",
                                "`process(String)` → adds `public static String doProcess(Service service, String input)`",
                                Fixtures.lineCol(source, offset))
                        .javaSection("Output", result)
                        .build()
        );
    }

    @Test
    void introduce_indirection_for_static_method() throws Exception {
        String source = fixtures.load("introduce-indirection/static-method/input/MathUtils.java");
        int offset = Fixtures.offsetOf(source, "square");

        String result = JdtIntroduceIndirection.introduceIndirection(
                source, "MathUtils.java", offset, "computeSquare");

        Approvals.verify(
                RefactoringStoryBoard.titled("Introduce indirection: MathUtils.square → static computeSquare(int)")
                        .javaSection("Input", source)
                        .refactoring("introduce indirection",
                                "`static square(int)` → adds `public static int computeSquare(int x)` that delegates",
                                Fixtures.lineCol(source, offset))
                        .javaSection("Output", result)
                        .build()
        );
    }

    @Test
    void introduce_indirection_rejected_when_no_method_at_offset() throws Exception {
        String source = fixtures.load("introduce-indirection/instance-method/input/Service.java");
        int offset = Fixtures.offsetOf(source, "class Service");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtIntroduceIndirection.introduceIndirection(
                        source, "Service.java", offset, "doProcess"))
                .actual();
        assertThat(ex.getMessage()).contains("No method declaration");

        Approvals.verify(
                RefactoringStoryBoard.titled("Introduce indirection rejected: no method at offset")
                        .javaSection("Input", source)
                        .refactoring("introduce indirection",
                                "offset inside class declaration, not a method",
                                Fixtures.lineCol(source, offset))
                        .diagnostic(ex.getMessage())
                        .build()
        );
    }
}
