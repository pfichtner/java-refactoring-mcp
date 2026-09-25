package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FIELD;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.TYPE;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.github.pfichtner.refactoring.FileChange;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Shared helpers used by the MCP tool handlers: result formatting and the
 * write-to-disk commit path. Refactoring logic lives in refactor-core.
 */
final class ToolSupport {

    private ToolSupport() {}

    static CallToolResult ok(String text) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder(text).build()))
                .isError(false)
                .build();
    }

    static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder("Error: " + message).build()))
                .isError(true)
                .build();
    }

    // -------------------------------------------------------------------------
    // Commit path (default, i.e. dryrun not set)
    // -------------------------------------------------------------------------

    /** Writes all changes, creating parent dirs and deleting moved-away originals. */
    static void writeChanges(List<FileChange> changes) throws Exception {
        for (FileChange fc : changes) {
            Files.createDirectories(fc.newPath().getParent());
            Files.writeString(fc.newPath(), fc.newSource());
            if (fc.pathChanged()) {
                Files.deleteIfExists(fc.oldPath());
            }
        }
    }

    /** Writes {@code changes} to disk and returns the apply summary (the default when dryrun is not set). */
    static String commit(List<FileChange> changes) throws Exception {
        writeChanges(changes);
        return formatSummary(changes);
    }

    /** A non-moving change: {@code path} is overwritten with {@code source}. */
    static FileChange overwrite(Path path, String source) {
        Path abs = path.toAbsolutePath().normalize();
        return new FileChange(abs, abs, source);
    }

    /** Converts a {@code Map<Path,String>} engine result into {@link FileChange}s. */
    static List<FileChange> changedMap(Map<Path, String> changed) {
        return changed.entrySet().stream()
                .map(e -> overwrite(e.getKey(), e.getValue()))
                .toList();
    }

    static String formatDryrun(Map<Path, String> changed) {
        if (changed.isEmpty()) return "No changes.";
        String names = changed.keySet().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .sorted()
                .collect(joining(", "));
        String content = changed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "\n=== " + e.getKey().getFileName() + " ===\n"
                        + e.getValue().stripTrailing() + "\n")
                .collect(joining());
        return "Dry run — no files written.\n"
                + "Would change (" + changed.size() + "): " + names + "\n"
                + content;
    }

    static String formatDryrun(List<FileChange> changed) {
        if (changed.isEmpty()) return "No changes.";
        String names = changed.stream()
                .map(FileChange::newPath)
                .map(Path::getFileName)
                .map(Path::toString)
                .sorted()
                .collect(joining(", "));
        String content = changed.stream()
                .sorted(comparing(fc -> fc.newPath().toString()))
                .map(fc -> "\n=== " + fc.newPath().getFileName() + " ===\n"
                        + fc.newSource().stripTrailing() + "\n")
                .collect(joining());
        return "Dry run — no files written.\n"
                + "Would change (" + changed.size() + "): " + names + "\n"
                + content;
    }

    static String formatSummary(List<FileChange> changed) {
        if (changed.isEmpty()) return "No changes.";
        String content = changed.stream()
                .sorted(comparing(fc -> fc.newPath().toString()))
                .map(ToolSupport::changeString)
                .collect(joining());
        return "Changed " + changed.size() + " file(s):\n" + content;
    }

	private static String changeString(FileChange fc) {
		return fc.pathChanged()
		        ? "  " + fc.oldPath().getFileName() + " → " + fc.newPath().getFileName() + "\n"
		        : "  " + fc.newPath().getFileName() + "\n";
	}
}