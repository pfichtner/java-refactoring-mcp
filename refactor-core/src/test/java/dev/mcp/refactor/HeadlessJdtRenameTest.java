package dev.mcp.refactor;

import org.approvaltests.Approvals;
import org.approvaltests.MarkdownStoryBoard;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Approval/storyboard tests for headless JDT rename.
 *
 * Each .approved.md shows: input source → named refactoring → output source.
 * Do NOT regenerate approved files to make tests pass — inspect the diff first.
 */
class HeadlessJdtRenameTest {

    @Test
    void rename_local_variable_from_declaration_site() throws Exception {
        String source = fixture("rename/local-variable/input/Foo.java");
        int offset = offsetOf(source, "int x") + "int ".length();
        String result = JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "answer");

        Approvals.verify(
            storyboard("Rename local variable: x → answer")
                .addCustomMarkdown(javaBlock("Input", source))
                .addCustomMarkdown(refactoringBlock("rename local variable",
                        "`x` → `answer`", "target: declaration site at " + lineCol(source, offset)))
                .addCustomMarkdown(javaBlock("Output", result))
        );
    }

    @Test
    void rename_local_variable_from_reference_site() throws Exception {
        String source = fixture("rename/local-variable/input/Foo.java");
        int offset = offsetOf(source, "x * 7");  // reference, not declaration
        String result = JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "answer");

        Approvals.verify(
            storyboard("Rename local variable: x → answer")
                .addCustomMarkdown(javaBlock("Input", source))
                .addCustomMarkdown(refactoringBlock("rename local variable",
                        "`x` → `answer`", "target: reference site at " + lineCol(source, offset)))
                .addCustomMarkdown(javaBlock("Output", result))
        );
    }

    @Test
    void rename_y_does_not_affect_x() throws Exception {
        String source = fixture("rename/local-variable/input/Foo.java");
        int offset = offsetOf(source, "int y") + "int ".length();
        String result = JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "product");

        Approvals.verify(
            storyboard("Rename local variable: y → product (x must be untouched)")
                .addCustomMarkdown(javaBlock("Input", source))
                .addCustomMarkdown(refactoringBlock("rename local variable",
                        "`y` → `product`", "target: declaration site at " + lineCol(source, offset)))
                .addCustomMarkdown(javaBlock("Output", result))
        );
    }

    @Test
    void rename_non_variable_is_rejected() throws Exception {
        String source = fixture("rename/local-variable/input/Foo.java");
        int offset = offsetOf(source, "class Foo") + "class ".length();

        String diagnostic;
        try {
            JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "Bar");
            diagnostic = "(no error — expected rejection)";
        } catch (IllegalArgumentException e) {
            diagnostic = e.getMessage();
        }

        Approvals.verify(
            storyboard("Rename type name — expect rejection")
                .addCustomMarkdown(javaBlock("Input", source))
                .addCustomMarkdown(refactoringBlock("rename",
                        "`Foo` → `Bar`", "target: type name at " + lineCol(source, offset)
                        + " (not a local variable or parameter)"))
                .addCustomMarkdown(diagnosticBlock(diagnostic))
        );
    }

    // --- helpers ---

    private static MarkdownStoryBoard storyboard(String title) {
        return new MarkdownStoryBoard().addTitle(title);
    }

    private static String javaBlock(String heading, String source) {
        return "\n\n### " + heading + ":\n```java\n" + source.stripTrailing() + "\n```";
    }

    private static String refactoringBlock(String operation, String rename, String detail) {
        return "\n\n### Refactoring:\n**" + operation + "** " + rename + "  \n" + detail;
    }

    private static String diagnosticBlock(String message) {
        return "\n\n### Diagnostic:\n```\n" + message + "\n```";
    }

    private static String lineCol(String source, int offset) {
        String before = source.substring(0, offset);
        int line = (int) before.chars().filter(c -> c == '\n').count() + 1;
        int col = offset - before.lastIndexOf('\n');
        return "line " + line + ", col " + col;
    }

    private String fixture(String relativePath) throws IOException, URISyntaxException {
        var url = getClass().getClassLoader().getResource("fixtures/" + relativePath);
        if (url == null) {
            throw new IllegalStateException("Fixture not found: fixtures/" + relativePath);
        }
        return Files.readString(Path.of(url.toURI()));
    }

    private static int offsetOf(String source, String token) {
        int idx = source.indexOf(token);
        if (idx < 0) {
            throw new IllegalStateException("Token not found in source: " + token);
        }
        return idx;
    }
}
