package com.github.pfichtner;

import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

/**
 * Approval/storyboard tests for headless JDT rename.
 *
 * Each .approved.md shows: input source → named refactoring → output source.
 * Do NOT regenerate approved files to make tests pass — inspect the diff first.
 */
class HeadlessJdtRenameTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    // --- local variable ---

    @Test
    void rename_local_variable_from_declaration_site() throws Exception {
        String source = fixtures.load("rename/local-variable/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "int x") + "int ".length();
        String result = JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "answer");

        Approvals.verify(
            RenameStoryBoard.titled("Rename local variable: x → answer")
                .javaSection("Input", source)
                .refactoring("rename local variable", "`x` → `answer`",
                        "target: declaration site at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void rename_local_variable_from_reference_site() throws Exception {
        String source = fixtures.load("rename/local-variable/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "x * 7");
        String result = JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "answer");

        Approvals.verify(
            RenameStoryBoard.titled("Rename local variable: x → answer")
                .javaSection("Input", source)
                .refactoring("rename local variable", "`x` → `answer`",
                        "target: reference site at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void rename_y_does_not_affect_x() throws Exception {
        String source = fixtures.load("rename/local-variable/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "int y") + "int ".length();
        String result = JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "product");

        Approvals.verify(
            RenameStoryBoard.titled("Rename local variable: y → product (x must be untouched)")
                .javaSection("Input", source)
                .refactoring("rename local variable", "`y` → `product`",
                        "target: declaration site at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    // --- parameter ---

    @Test
    void rename_parameter_n_to_count() throws Exception {
        String source = fixtures.load("rename/parameter/input/Counter.java");
        int offset = Fixtures.offsetOf(source, "int n") + "int ".length();
        String result = JdtRenamer.renameLocalVariable(source, "Counter.java", offset, "count");

        Approvals.verify(
            RenameStoryBoard.titled("Rename parameter: n → count")
                .javaSection("Input", source)
                .refactoring("rename parameter", "`n` → `count`",
                        "target: declaration site at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    // --- precondition failures ---

    @Test
    void rename_non_variable_is_rejected() throws Exception {
        String source = fixtures.load("rename/local-variable/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "class Foo") + "class ".length();

        String diagnostic;
        try {
            JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "Bar");
            diagnostic = "(no error — expected rejection)";
        } catch (IllegalArgumentException e) {
            diagnostic = e.getMessage();
        }

        Approvals.verify(
            RenameStoryBoard.titled("Rename type name — expect rejection")
                .javaSection("Input", source)
                .refactoring("rename", "`Foo` → `Bar`",
                        "target: type name at " + Fixtures.lineCol(source, offset)
                        + " (not a local variable or parameter)")
                .diagnostic(diagnostic)
                .build()
        );
    }
}
