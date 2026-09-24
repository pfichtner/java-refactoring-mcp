package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Files;
import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtConvertNestedToTopLevel;
import com.github.pfichtner.refactoring.locator.LocatorResolver;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ConvertNestedToTopLevelTool}.
 */
public final class ConvertNestedToTopLevelTool {

    static SyncToolSpecification convertNestedToTopLevel() {
        Options opts = Options.of(FILE, LINE, COLUMN);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_nested_to_top_level", opts.toSchema())
                .description("Convert a nested (member) type to a top-level type. Returns both the modified outer source and the new type's source.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        Path file  = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset = LocatorResolver.resolve(args.getLocator(), source, file.getFileName().toString());
                        JdtConvertNestedToTopLevel.Result result =
                                JdtConvertNestedToTopLevel.convert(
                                        source, file.getFileName().toString(), offset);
                        String out = "=== " + file.getFileName() + " (modified) ===\n"
                                + result.outerSource()
                                + "\n=== " + result.newTypeName() + ".java (new file) ===\n"
                                + result.newTypeSource();
                        return ok(out);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
