package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

/**
 * Approval tests for Convert to Static Import.
 * Each .approved.md shows: input → offset context → output (or diagnostic on rejection).
 */
class ConvertToStaticImportTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void convert_single_occurrence_adds_import_and_removes_qualifier() throws Exception {
        String source = fixtures.load("convert-to-static-import/simple/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "Collectors.joining(") + "Collectors.".length();

        String result = JdtConvertToStaticImport.convertToStaticImport(
                source, "Foo.java", offset, false);

        Approvals.verify(
            RefactoringStoryBoard.titled("Convert to static import: Collectors.joining — single occurrence")
                .javaSection("Input", source)
                .refactoring("convert to static import",
                        "`Collectors.joining` at " + Fixtures.lineCol(source, offset),
                        "import added; qualifier removed from one call")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void convert_replace_all_removes_qualifier_from_every_call() throws Exception {
        String source = fixtures.load("convert-to-static-import/replace-all/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "Collectors.joining(") + "Collectors.".length();

        String result = JdtConvertToStaticImport.convertToStaticImport(
                source, "Foo.java", offset, true);

        Approvals.verify(
            RefactoringStoryBoard.titled("Convert to static import: Collectors.joining — all occurrences")
                .javaSection("Input", source)
                .refactoring("convert to static import",
                        "`Collectors.joining` — replace all",
                        "import added once; qualifier removed from both calls")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void convert_single_when_import_already_exists_does_not_duplicate() throws Exception {
        String source = fixtures.load(
                "convert-to-static-import/import-already-exists/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "Collectors.joining(") + "Collectors.".length();

        String result = JdtConvertToStaticImport.convertToStaticImport(
                source, "Foo.java", offset, false);

        Approvals.verify(
            RefactoringStoryBoard.titled(
                    "Convert to static import: import already present — qualifier removed, no duplicate import")
                .javaSection("Input", source)
                .refactoring("convert to static import",
                        "`Collectors.joining` — import already exists",
                        "qualifier removed; existing import kept as-is")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void convert_replace_all_when_import_already_exists() throws Exception {
        String source = fixtures.load(
                "convert-to-static-import/import-already-exists-replace-all/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "Collectors.joining(") + "Collectors.".length();

        String result = JdtConvertToStaticImport.convertToStaticImport(
                source, "Foo.java", offset, true);

        Approvals.verify(
            RefactoringStoryBoard.titled(
                    "Convert to static import: import already present, replace all — no duplicate import")
                .javaSection("Input", source)
                .refactoring("convert to static import",
                        "`Collectors.joining` — import already exists, replace all",
                        "qualifier removed from all calls; existing import kept as-is")
                .javaSection("Output", result)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    @Test
    void convert_rejected_offset_not_on_method_call() throws Exception {
        String source = fixtures.load(
                "convert-to-static-import/invalid-not-a-method-call/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "LIMIT");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtConvertToStaticImport.convertToStaticImport(
                        source, "Foo.java", offset, false))
                .actual();
        assertThat(ex.getMessage()).containsAnyOf("No method call", "No AST node");

        Approvals.verify(
            RefactoringStoryBoard.titled("Convert to static import — rejected: offset not on a method call")
                .javaSection("Input", source)
                .refactoring("convert to static import",
                        "`LIMIT` at " + Fixtures.lineCol(source, offset),
                        "field reference, not a method call")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void convert_rejected_instance_method() throws Exception {
        String source = fixtures.load(
                "convert-to-static-import/invalid-not-static/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "toLowerCase");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtConvertToStaticImport.convertToStaticImport(
                        source, "Foo.java", offset, false))
                .actual();
        assertThat(ex.getMessage()).contains("not a static method");

        Approvals.verify(
            RefactoringStoryBoard.titled("Convert to static import — rejected: instance method")
                .javaSection("Input", source)
                .refactoring("convert to static import",
                        "`s.toLowerCase()` at " + Fixtures.lineCol(source, offset),
                        "only static methods can be statically imported")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void convert_rejected_already_bare() throws Exception {
        String source = fixtures.load(
                "convert-to-static-import/invalid-already-bare/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "joining(");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtConvertToStaticImport.convertToStaticImport(
                        source, "Foo.java", offset, false))
                .actual();
        assertThat(ex.getMessage()).contains("already a bare");

        Approvals.verify(
            RefactoringStoryBoard.titled("Convert to static import — rejected: call already bare")
                .javaSection("Input", source)
                .refactoring("convert to static import",
                        "`joining` at " + Fixtures.lineCol(source, offset),
                        "call is already unqualified — nothing to do")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void convert_rejected_name_clash_with_existing_static_import() throws Exception {
        String source = fixtures.load(
                "convert-to-static-import/invalid-name-clash/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "Collections.sort") + "Collections.".length();

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtConvertToStaticImport.convertToStaticImport(
                        source, "Foo.java", offset, false))
                .actual();
        assertThat(ex.getMessage()).contains("sort");

        Approvals.verify(
            RefactoringStoryBoard.titled(
                    "Convert to static import — rejected: name clash with existing static import")
                .javaSection("Input", source)
                .refactoring("convert to static import",
                        "`Collections.sort` at " + Fixtures.lineCol(source, offset),
                        "'sort' already statically imported from java.util.Arrays")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
