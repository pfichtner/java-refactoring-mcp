package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.project.MavenProject;
import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

class ConvertToRecordTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void convert_class_to_record_renames_getter_call_sites() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/convert-to-record");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot  = project.sourceRoots().get(0);
        Path pointFile = srcRoot.resolve("com/example/Point.java");
        Path appFile   = srcRoot.resolve("com/example/App.java");

        String pointSrc = Files.readString(pointFile);
        String appSrc   = Files.readString(appFile);

        Map<Path, String> changed = JdtConvertToRecord.convertToRecord(project, pointFile);

        Path absPoint = pointFile.toAbsolutePath().normalize();
        Path absApp   = appFile.toAbsolutePath().normalize();

        assertThat(changed.containsKey(absPoint)).as("Point.java must be in result").isTrue();
        assertThat(changed.containsKey(absApp)).as("App.java must be in result").isTrue();

        Approvals.verify(
            RefactoringStoryBoard.titled("Convert class to record: Point (getters renamed at call sites)")
                .inputProject(Map.of("App.java", appSrc, "Point.java", pointSrc))
                .refactoring("convert to record",
                    "`class Point` → `record Point(int x, int y)`; `getX()`→`x()`, `getY()`→`y()`",
                    "fields, constructor, and bean getters removed; App.java call sites renamed")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void convert_to_record_rejected_when_class_has_extends() throws Exception {
        Path projectRoot  = fixtures.projectPath("projects/convert-to-record");
        MavenProject project = new MavenProject(projectRoot);
        Path derivedFile  = project.sourceRoots().get(0).resolve("com/example/Derived.java");
        String source     = Files.readString(derivedFile);

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtConvertToRecord.convertToRecord(project, derivedFile)).actual();
        assertThat(ex.getMessage()).contains("extends");

        Approvals.verify(
            RefactoringStoryBoard.titled("Convert to record rejected: class has extends clause")
                .javaSection("Input: Derived.java", source)
                .refactoring("convert to record", "`Derived` extends `Base`",
                    "records cannot extend classes")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void convert_to_record_rejected_when_has_non_private_final_fields() throws Exception {
        Path projectRoot   = fixtures.projectPath("projects/convert-to-record");
        MavenProject project = new MavenProject(projectRoot);
        Path mutableFile   = project.sourceRoots().get(0).resolve("com/example/Mutable.java");
        String source      = Files.readString(mutableFile);

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtConvertToRecord.convertToRecord(project, mutableFile)).actual();
        assertThat(ex.getMessage()).contains("non-private-final field(s)");

        Approvals.verify(
            RefactoringStoryBoard.titled("Convert to record rejected: has non-private-final fields")
                .javaSection("Input: Mutable.java", source)
                .refactoring("convert to record", "`Mutable` has non-private-final field(s): count", "")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
