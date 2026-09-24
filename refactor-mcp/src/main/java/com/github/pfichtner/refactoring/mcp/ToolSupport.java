package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FIELD;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.NEW_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.REFACTORING;
import static com.github.pfichtner.refactoring.mcp.Property.TYPE;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtRenamer;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Shared helpers used by the MCP tool handlers: result formatting, the
 * rename engine call, and the schema for {@code analyze_refactoring} /
 * {@code apply_refactoring}.
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

    static List<FileChange> executeRename(Options.Reader args)
            throws Exception {
        Path filePath      = args.getPath(FILE);
        String newName     = args.getString(NEW_NAME);
        String refactoring = args.getString(REFACTORING, "rename");

        if (!"rename".equals(refactoring)) {
            throw new IllegalArgumentException(
                    "Unsupported refactoring type: '" + refactoring
                    + "'. Supported: rename");
        }

        Path root = args.getPath(PROJECT_ROOT);
        SourceFile source = new SourceFile(
                filePath.isAbsolute() ? filePath : root.resolve(filePath));

        int offset = source.resolve(args.getLocator());

        return JdtRenamer.rename(ProjectDetector.detect(root), source.path(), offset, newName);
    }

    static Options renameOptions() {
        return Options.builder()
                .add(LINE, COLUMN, METHOD, FIELD, TYPE, CLASS)
                .addRequired(PROJECT_ROOT, FILE, REFACTORING, NEW_NAME)
                .build();
    }

    static String formatPreview(Map<Path, String> changed) {
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

    static String formatPreview(List<FileChange> changed) {
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
        return "Renamed in " + changed.size() + " file(s):\n" + content;
    }

	private static String changeString(FileChange fc) {
		return fc.pathChanged()
		        ? "  " + fc.oldPath().getFileName() + " → " + fc.newPath().getFileName() + "\n"
		        : "  " + fc.newPath().getFileName() + "\n";
	}
}