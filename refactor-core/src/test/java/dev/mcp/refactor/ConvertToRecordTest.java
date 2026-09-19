package dev.mcp.refactor;

import dev.mcp.refactor.support.Fixtures;
import dev.mcp.refactor.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConvertToRecordTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void convert_class_to_record_removes_fields_ctor_and_simple_getters() throws Exception {
        String source = fixtures.load("convert-to-record/point/Point.java");

        String result = JdtConvertToRecord.convertToRecord(source, "Point.java");

        assertTrue(result.contains("record Point(int x, int y)"),
                "record declaration with components");
        assertFalse(result.contains("private final int x"),
                "fields should be removed");
        assertFalse(result.contains("public Point("),
                "constructor should be removed");
        assertFalse(result.contains("getX()"),
                "bean-style getter should be removed");
        assertTrue(result.contains("distanceTo"),
                "custom method should be kept");

        Approvals.verify(
            RenameStoryBoard.titled("Convert class to record: Point")
                .javaSection("Input: Point.java", source)
                .refactoring("convert to record",
                    "`class Point` → `record Point(int x, int y)`",
                    "removes fields, all-args constructor, and simple getters; keeps distanceTo")
                .javaSection("Output: Point.java", result)
                .build()
        );
    }

    @Test
    void convert_to_record_rejected_when_class_has_extends() throws Exception {
        String source = fixtures.load("convert-to-record/with-extends/Derived.java");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtConvertToRecord.convertToRecord(source, "Derived.java"));
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
        String source = fixtures.load("convert-to-record/no-private-final-fields/Mutable.java");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtConvertToRecord.convertToRecord(source, "Mutable.java"));
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
