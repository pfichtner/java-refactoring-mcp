package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.NESTED_CLASS_NAME;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;


import com.github.pfichtner.refactoring.JdtConvertAnonymousToNested;
import com.github.pfichtner.refactoring.locator.LocatorResolver;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ConvertAnonymousToNestedTool}.
 */
public final class ConvertAnonymousToNestedTool {

    static SyncToolSpecification convertAnonymousToNested() {
        Options opts = Options.of(FILE, LINE, COLUMN, NESTED_CLASS_NAME);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_anonymous_to_nested", opts.toSchema())
                .description("Convert an anonymous class at the given position to a private named nested class.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args          = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        int offset        = LocatorResolver.resolve(args.getLocator(), source.content(), source.path().getFileName().toString());
                        String nestedName = args.getString(NESTED_CLASS_NAME);
                        String result     = JdtConvertAnonymousToNested.convert(
                                source.content(), source.path().getFileName().toString(), offset, nestedName);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
