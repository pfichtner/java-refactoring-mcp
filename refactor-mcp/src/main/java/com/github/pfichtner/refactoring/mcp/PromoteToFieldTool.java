package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Files;
import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtPromoteToField;
import com.github.pfichtner.refactoring.locator.LocatorResolver;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code PromoteToFieldTool}.
 */
public final class PromoteToFieldTool {

    static SyncToolSpecification promoteToField() {
        Options opts = Options.of(FILE, LINE, COLUMN);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("promote_to_field", opts.toSchema())
                .description("Promote a local variable declaration to a private instance field of the enclosing class.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args    = opts.reader(request.arguments());
                        Path file   = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset  = LocatorResolver.resolve(args.getLocator(), source, file.getFileName().toString());
                        String result = JdtPromoteToField.promote(
                                source, file.getFileName().toString(), offset);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
