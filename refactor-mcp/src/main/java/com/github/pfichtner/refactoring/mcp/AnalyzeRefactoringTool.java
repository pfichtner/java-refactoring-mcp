package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.executeRename;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatPreview;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.renameOptions;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code AnalyzeRefactoringTool}.
 */
public final class AnalyzeRefactoringTool {

    static SyncToolSpecification analyzeRefactoring() {
        Options opts = renameOptions();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("analyze_refactoring", opts.toSchema())
                        .description("""
                        Preview what a refactoring would change without modifying files on disk.
                        Returns the new source of every file that would be affected.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var changed = executeRename(opts.reader(request.arguments()));
                        return ok(formatPreview(changed));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
