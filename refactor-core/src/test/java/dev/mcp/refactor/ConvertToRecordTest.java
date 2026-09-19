package dev.mcp.refactor;

import dev.mcp.refactor.project.MavenProject;
import dev.mcp.refactor.support.Fixtures;
import dev.mcp.refactor.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

        assertTrue(changed.containsKey(absPoint), "Point.java must be in result");
        assertTrue(changed.containsKey(absApp),   "App.java must be in result");

        Approvals.verify(
            RenameStoryBoard.titled("Convert class to record: Point (getters renamed at call sites)")
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

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtConvertToRecord.convertToRecord(project, derivedFile));
        assertTrue(ex.getMessage().contains("extends"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Convert to record rejected: class has extends clause")
                .javaSection("Input: Derived.java", source)
                .refactoring("convert to record", "`Derived` extends `Base`",
                    "records cannot extend classes")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void convert_to_record_rejected_when_no_private_final_fields() throws Exception {
        Path projectRoot   = fixtures.projectPath("projects/convert-to-record");
        MavenProject project = new MavenProject(projectRoot);
        Path mutableFile   = project.sourceRoots().get(0).resolve("com/example/Mutable.java");
        String source      = Files.readString(mutableFile);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtConvertToRecord.convertToRecord(project, mutableFile));
        assertTrue(ex.getMessage().contains("private final"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Convert to record rejected: no private final fields")
                .javaSection("Input: Mutable.java", source)
                .refactoring("convert to record", "`Mutable` has no private final fields", "")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
