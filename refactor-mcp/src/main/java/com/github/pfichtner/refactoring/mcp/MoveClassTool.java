package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.NEW_PACKAGE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.WIDEN_VISIBILITY;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtMoveClass;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code MoveClassTool}.
 */
public final class MoveClassTool {

    static SyncToolSpecification moveClass() {
        Options opts = Options.builder().add(DRY_RUN, WIDEN_VISIBILITY).addRequired(PROJECT_ROOT, FILE, NEW_PACKAGE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_class", opts.toSchema())
                        .description("""
                        Move a Java class to a new package.
                        Updates the package declaration and all explicit imports in the project.
                        Returns: new class source, new canonical file path, updated import files.
                        Applies by default: writes the class to the new path, deletes the original, and updates the imports; pass dryrun=true to preview instead.
                        widen_visibility (default true): if the class is package-private, adds 'public'.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args       = opts.reader(request.arguments());
                    Path file         = args.getPath(FILE);
                    String newPackage = args.getString(NEW_PACKAGE);
                    boolean widen     = args.getBoolean(WIDEN_VISIBILITY, true);
                    var result = JdtMoveClass.moveClass(
                            ProjectDetector.detect(
                                    args.getPath(PROJECT_ROOT)),
                            file, newPackage, widen);

                    return args.getBoolean(DRY_RUN)
                            ? ok(("New path: " + result.newFilePath() + "\n\n"
                                    + "=== " + result.newFilePath().getFileName() + " (new) ===\n"
                                    + result.newClassSource().stripTrailing() + "\n"
                                    + result.changedImports().entrySet().stream()
                                            .sorted(Map.Entry.comparingByKey())
                                            .map(e -> "\n=== " + e.getKey().getFileName() + " (updated import) ===\n"
                                                    + e.getValue().stripTrailing() + "\n")
                                            .collect(Collectors.joining())).stripTrailing())
                            : ok(commit(Stream.concat(
                                    Stream.of(new FileChange(file, result.newFilePath(), result.newClassSource())),
                                    result.changedImports().entrySet().stream()
                                            .map(e -> overwrite(e.getKey(), e.getValue())))
                                    .toList()));
                }))
                .build();
    }
}
