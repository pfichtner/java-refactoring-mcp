package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.VARIABLE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Files;
import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtInliner;
import com.github.pfichtner.refactoring.locator.LocatorResolver;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code InlineVariableTool}.
 */
public final class InlineVariableTool {

    static SyncToolSpecification inlineVariable() {
        Options opts = Options.builder().add(LINE, COLUMN, VARIABLE).addRequired(FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_variable", opts.toSchema())
                        .description("""
                        Inline a local variable: replace every use with its initializer expression
                        and remove the declaration. Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        Path file   = args.getPath(FILE);

                        String source = Files.readString(file);
                        int offset    = LocatorResolver.resolve(args.getLocator(), source, file.getFileName().toString());
                        String result = JdtInliner.inlineVariable(
                                source, file.getFileName().toString(), offset);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
