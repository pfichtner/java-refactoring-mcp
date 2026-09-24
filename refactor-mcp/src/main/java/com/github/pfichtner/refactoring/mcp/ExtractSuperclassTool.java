package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD_NAMES;
import static com.github.pfichtner.refactoring.mcp.Property.SUPERCLASS_NAME;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.JdtExtractSuperclass;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ExtractSuperclassTool}.
 */
public final class ExtractSuperclassTool {

    static SyncToolSpecification extractSuperclass() {
        Options opts = Options.builder().add(METHOD_NAMES).addRequired(FILE, SUPERCLASS_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_superclass", opts.toSchema())
                        .description("""
                        Extract an abstract superclass from selected methods of a class.
                        Returns the modified class source and the new superclass source.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = opts.reader(request.arguments());
                        Path file            = args.getPath(FILE);
                        String superName     = args.getString(SUPERCLASS_NAME);
                        List<String> methods = args.getStringList(METHOD_NAMES);
                        String source = Files.readString(file);
                        var result = JdtExtractSuperclass.extractSuperclass(
                                source, file.getFileName().toString(), superName, methods);
                        String out = "=== " + file.getFileName() + " (modified) ===\n"
                                + result.modifiedClassSource().stripTrailing() + "\n\n"
                                + "=== " + superName + ".java (new) ===\n"
                                + result.superclassSource().stripTrailing();
                        return ok(out);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
