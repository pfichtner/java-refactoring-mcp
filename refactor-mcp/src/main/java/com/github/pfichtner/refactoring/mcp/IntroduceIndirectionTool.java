package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.INDIRECTION_METHOD_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import com.github.pfichtner.refactoring.JdtIntroduceIndirection;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code IntroduceIndirectionTool}.
 */
public final class IntroduceIndirectionTool {

    static SyncToolSpecification introduceIndirection() {
        Options opts = Options.of(FILE, LINE, COLUMN, INDIRECTION_METHOD_NAME);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_indirection", opts.toSchema())
                .description("Add a public static indirection (wrapper) method that delegates to the method at the given position.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args    = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        int offset  = source.resolve(args.getLocator());
                        String name = args.getString(INDIRECTION_METHOD_NAME);
                        String result = JdtIntroduceIndirection.introduceIndirection(
                                source.content(), source.path().getFileName().toString(), offset, name);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
