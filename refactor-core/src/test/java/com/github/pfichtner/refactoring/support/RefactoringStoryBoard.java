package com.github.pfichtner.refactoring.support;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.approvaltests.MarkdownStoryBoard;

import com.github.pfichtner.refactoring.FileChange;

/**
 * Fluent builder for rename approval storyboards.
 *
 * Each storyboard documents one rename scenario in a single .approved.md:
 *   input source → named refactoring → output source (or diagnostic on rejection).
 */
public class RefactoringStoryBoard {

    private final MarkdownStoryBoard board;

    private RefactoringStoryBoard(String title) {
        this.board = new MarkdownStoryBoard().addTitle(title);
    }

    public static RefactoringStoryBoard titled(String title) {
        return new RefactoringStoryBoard(title);
    }

    /** Adds a Java source block under the given heading (e.g. "Input" or "Output"). */
    public RefactoringStoryBoard javaSection(String heading, String source) {
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
    public RefactoringStoryBoard refactoring(String operation, String rename, String detail) {
        board.addCustomMarkdown("\n\n### Refactoring:\n**" + operation + "** "
                + rename + "  \n" + detail);
        return this;
    }

    /**
     * Adds one {@code ### Input: filename:} java block per entry, sorted by filename.
     * Use for multi-file project scenarios.
     */
    public RefactoringStoryBoard inputProject(Map<String, String> files) {
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
    public RefactoringStoryBoard outputProject(Map<Path, String> changedFiles) {
        changedFiles.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getFileName().toString()))
                .forEach(e -> board.addCustomMarkdown(
                        "\n\n### Output: " + e.getKey().getFileName() + ":\n```java\n"
                        + e.getValue().stripTrailing() + "\n```"));
        return this;
    }

    /**
     * Adds one {@code ### Output: filename:} java block per changed file (using new path),
     * sorted by new filename.
     */
    public RefactoringStoryBoard outputProject(List<FileChange> changes) {
        changes.stream()
                .sorted(Comparator.comparing(fc -> fc.newPath().getFileName().toString()))
                .forEach(fc -> board.addCustomMarkdown(
                        "\n\n### Output: " + fc.newPath().getFileName() + ":\n```java\n"
                        + fc.newSource().stripTrailing() + "\n```"));
        return this;
    }

    /**
     * Adds a {@code ### Filesystem:} section listing files whose path changed (e.g. class rename).
     * No-op when no paths changed.
     */
    public RefactoringStoryBoard filesystemSection(List<FileChange> changes) {
        List<FileChange> moved = changes.stream().filter(FileChange::pathChanged).toList();
        if (moved.isEmpty()) return this;
        String filesystemMd = moved.stream()
                .sorted(Comparator.comparing(fc -> fc.oldPath().getFileName().toString()))
                .map(fc -> "- `" + fc.oldPath().getFileName() + "` → `" + fc.newPath().getFileName() + "`")
                .collect(Collectors.joining("\n"));
        board.addCustomMarkdown("\n\n### Filesystem:\n" + filesystemMd);
        return this;
    }

    /** Adds a Diagnostic section for rejection/precondition-failure tests. */
    public RefactoringStoryBoard diagnostic(String message) {
        board.addCustomMarkdown("\n\n### Diagnostic:\n```\n" + message + "\n```");
        return this;
    }

    public MarkdownStoryBoard build() {
        return board;
    }
}
