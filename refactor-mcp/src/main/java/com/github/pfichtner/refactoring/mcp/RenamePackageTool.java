package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.NEW_PACKAGE;
import static com.github.pfichtner.refactoring.mcp.Property.OLD_PACKAGE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.util.stream.Collectors;

import com.github.pfichtner.refactoring.JdtRenamePackage;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code RenamePackageTool}.
 */
public final class RenamePackageTool {

    static SyncToolSpecification renamePackage() {
        Options opts = Options.builder().add(DRY_RUN).addRequired(PROJECT_ROOT, OLD_PACKAGE, NEW_PACKAGE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("rename_package", opts.toSchema())
                        .description("""
                        Rename a package across the project.
                        Updates package declarations, single-class imports, and wildcard imports.
                        Returns a list of changed files with new sources and new paths.
                        Applies by default and moves the files; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args = opts.reader(request.arguments());
                    var result = JdtRenamePackage.renamePackage(
                            ProjectDetector.detect(
                                    args.getPath(PROJECT_ROOT)),
                            args.getString(OLD_PACKAGE),
                            args.getString(NEW_PACKAGE));

                    return args.getBoolean(DRY_RUN)
                            ? ok(result.changedFiles().stream()
                                    .map(fc -> "=== " + fc.newPath().getFileName()
                                            + (fc.pathChanged() ? " (moved from " + fc.oldPath().getFileName() + ")" : "")
                                            + " ===\n" + fc.newSource().stripTrailing() + "\n\n")
                                    .collect(Collectors.joining()).stripTrailing())
                            : ok(commit(result.changedFiles()));
                }))
                .build();
    }
}
