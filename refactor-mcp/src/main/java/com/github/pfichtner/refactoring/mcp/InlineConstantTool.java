package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FIELD;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.REMOVE_DECLARATION;
import static com.github.pfichtner.refactoring.mcp.Property.REPLACE_ALL;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;


import com.github.pfichtner.refactoring.JdtInliner;
import com.github.pfichtner.refactoring.locator.LocatorResolver;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code InlineConstantTool}.
 */
public final class InlineConstantTool {

    static SyncToolSpecification inlineConstant() {
        Options opts = Options.builder().add(LINE, COLUMN, FIELD, CLASS, REPLACE_ALL, REMOVE_DECLARATION).addRequired(FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_constant", opts.toSchema())
                        .description("""
                        Inline a static final constant: replace one or all references with its
                        initializer expression, and optionally remove the field declaration.
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args       = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        boolean allOcc = args.getBoolean(REPLACE_ALL);
                        boolean removeDecl = args.getBoolean(REMOVE_DECLARATION);

                        int offset    = LocatorResolver.resolve(args.getLocator(), source.content(), source.path().getFileName().toString());
                        String result = JdtInliner.inlineConstant(
                                source.content(), source.path().getFileName().toString(), offset, allOcc, removeDecl);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
