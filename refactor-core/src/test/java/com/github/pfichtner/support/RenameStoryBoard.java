package com.github.pfichtner.support;

import org.approvaltests.MarkdownStoryBoard;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

/**
 * Fluent builder for rename approval storyboards.
 *
 * Each storyboard documents one rename scenario in a single .approved.md:
 *   input source → named refactoring → output source (or diagnostic on rejection).
 */
public class RenameStoryBoard {

    private final MarkdownStoryBoard board;

    private RenameStoryBoard(String title) {
        this.board = new MarkdownStoryBoard().addTitle(title);
    }

    public static RenameStoryBoard titled(String title) {
        return new RenameStoryBoard(title);
    }

    /** Adds a Java source block under the given heading (e.g. "Input" or "Output"). */
    public RenameStoryBoard javaSection(String heading, String source) {
        board.addCustomMarkdown("\n\n### " + heading + ":\n```java\n"
                + source.stripTrailing() + "\n```");
        return this;
    }

    /**
     * Adds the "Refactoring" section describing the operation being applied.
     *
     * @param operation short name, e.g. {@code "rename local variable"}
     * @param rename    backtick-formatted rename, e.g. {@code "`x` → `answer`"}
     * @param detail    extra context, e.g. target location from {@link Fixtures#lineCol}
     */
    public RenameStoryBoard refactoring(String operation, String rename, String detail) {
        board.addCustomMarkdown("\n\n### Refactoring:\n**" + operation + "** "
                + rename + "  \n" + detail);
        return this;
    }

    /**
     * Adds one {@code ### Input: filename:} java block per entry, sorted by filename.
     * Use for multi-file project scenarios.
     */
    public RenameStoryBoard inputProject(Map<String, String> files) {
        files.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> board.addCustomMarkdown(
                        "\n\n### Input: " + e.getKey() + ":\n```java\n"
                        + e.getValue().stripTrailing() + "\n```"));
        return this;
    }

    /**
     * Adds one {@code ### Output: filename:} java block per changed file, sorted by filename.
     * Use for multi-file rename results.
     */
    public RenameStoryBoard outputProject(Map<Path, String> changedFiles) {
        changedFiles.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getFileName().toString()))
                .forEach(e -> board.addCustomMarkdown(
                        "\n\n### Output: " + e.getKey().getFileName() + ":\n```java\n"
                        + e.getValue().stripTrailing() + "\n```"));
        return this;
    }

    /** Adds a Diagnostic section for rejection/precondition-failure tests. */
    public RenameStoryBoard diagnostic(String message) {
        board.addCustomMarkdown("\n\n### Diagnostic:\n```\n" + message + "\n```");
        return this;
    }

    public MarkdownStoryBoard build() {
        return board;
    }
}
