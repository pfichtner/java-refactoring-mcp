package dev.mcp.refactor;

import dev.mcp.refactor.support.Fixtures;
import dev.mcp.refactor.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Approval tests for Extract Variable.
 * Each .approved.md shows: input → selected expression + variable name → output.
 */
class ExtractVariableTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void extract_simple_expression() throws Exception {
        String source = fixtures.load("extract-var/simple/input/Foo.java");
        int start = Fixtures.offsetOf(source, "6 * 7");
        int len   = "6 * 7".length();

        String result = JdtExtractVariable.extractVariable(
                source, "Foo.java", start, len, "answer", false);

        Approvals.verify(
            RenameStoryBoard.titled("Extract variable: 6 * 7 → answer")
                .javaSection("Input", source)
                .refactoring("extract variable", "`6 * 7` → `int answer`",
                        Fixtures.lineCol(source, start))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void extract_replaces_all_occurrences() throws Exception {
        String source = fixtures.load("extract-var/replaces-all-occurrences/input/Foo.java");
        int start = Fixtures.offsetOf(source, "42");
        int len   = "42".length();

        String result = JdtExtractVariable.extractVariable(
                source, "Foo.java", start, len, "MAGIC", true);

        Approvals.verify(
            RenameStoryBoard.titled("Extract variable: 42 → MAGIC (replace all occurrences)")
                .javaSection("Input", source)
                .refactoring("extract variable", "`42` → `int MAGIC` (replaceAll=true)",
                        Fixtures.lineCol(source, start))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void extract_nested_expression() throws Exception {
        String source = fixtures.load("extract-var/nested-expression/input/Foo.java");
        int start = Fixtures.offsetOf(source, "name.toUpperCase()");
        int len   = "name.toUpperCase()".length();

        String result = JdtExtractVariable.extractVariable(
                source, "Foo.java", start, len, "upper", false);

        Approvals.verify(
            RenameStoryBoard.titled("Extract variable: name.toUpperCase() → upper")
                .javaSection("Input", source)
                .refactoring("extract variable", "`name.toUpperCase()` → `String upper`",
                        Fixtures.lineCol(source, start))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void extract_rejected_when_selection_is_simple_name() throws Exception {
        String source = fixtures.load("extract-var/invalid-not-expression/input/Foo.java");
        // Selecting the variable 'x' — already a simple name, nothing to extract
        int start = Fixtures.offsetOf(source, "int x") + "int ".length();
        int len   = "x".length();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtExtractVariable.extractVariable(
                        source, "Foo.java", start, len, "y", false));
        assertTrue(ex.getMessage().contains("simple name"));

        Approvals.verify(
            RenameStoryBoard.titled("Extract variable — rejected: selection is a simple name")
                .javaSection("Input", source)
                .refactoring("extract variable", "`x` → `y` (simple name — nothing to extract)",
                        Fixtures.lineCol(source, start))
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void extract_rejected_when_selection_is_assignment() throws Exception {
        String source = fixtures.load("extract-var/invalid-statement/input/Foo.java");
        // Selecting the assignment expression "x = 2"
        int start = Fixtures.offsetOf(source, "x = 2");
        int len   = "x = 2".length();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtExtractVariable.extractVariable(
                        source, "Foo.java", start, len, "val", false));
        assertTrue(ex.getMessage().contains("assignment"));

        Approvals.verify(
            RenameStoryBoard.titled("Extract variable — rejected: selection is an assignment")
                .javaSection("Input", source)
                .refactoring("extract variable", "`x = 2` → `val` (assignment — cannot extract)",
                        Fixtures.lineCol(source, start))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
