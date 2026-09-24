package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.executeRename;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatSummary;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.renameOptions;

import java.nio.file.Files;

import com.github.pfichtner.refactoring.FileChange;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ApplyRefactoringTool}.
 */
public final class ApplyRefactoringTool {

    static SyncToolSpecification applyRefactoring() {
        Options opts = renameOptions();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("apply_refactoring", opts.toSchema())
                        .description("""
                        Apply a refactoring and write the changes to disk.
                        Returns a summary of which files were modified.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var changed = executeRename(opts.reader(request.arguments()));
                        for (FileChange fc : changed) {
                            Files.createDirectories(fc.newPath().getParent());
                            Files.writeString(fc.newPath(), fc.newSource());
                            if (fc.pathChanged()) Files.deleteIfExists(fc.oldPath());
                        }
                        return ok(formatSummary(changed));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
