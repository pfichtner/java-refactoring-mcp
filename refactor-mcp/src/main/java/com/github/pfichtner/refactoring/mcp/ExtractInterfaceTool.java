package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.INTERFACE_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD_NAMES;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.util.List;

import com.github.pfichtner.refactoring.JdtExtractInterface;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ExtractInterfaceTool}.
 */
public final class ExtractInterfaceTool {

    static SyncToolSpecification extractInterface() {
        Options opts = Options.builder().add(METHOD_NAMES).addRequired(FILE, INTERFACE_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_interface", opts.toSchema())
                        .description("""
                        Extract a new interface from the public methods of a class.
                        Returns the modified class source and the new interface source.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        String interfaceName  = args.getString(INTERFACE_NAME);
                        List<String> methods  = args.getStringList(METHOD_NAMES);
                        var result = JdtExtractInterface.extractInterface(
                                source.content(), source.path().getFileName().toString(),
                                interfaceName, methods);

                        String out = "=== " + source.path().getFileName() + " (modified) ===\n"
                                + result.modifiedClassSource().stripTrailing() + "\n\n"
                                + "=== " + interfaceName + ".java (new) ===\n"
                                + result.interfaceSource().stripTrailing();
                        return ok(out);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
