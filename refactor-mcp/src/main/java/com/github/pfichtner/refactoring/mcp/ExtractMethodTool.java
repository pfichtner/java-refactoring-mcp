package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.END_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.END_LINE;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.START_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.START_LINE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Files;
import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtExtractor;
import com.github.pfichtner.refactoring.JdtRenamer;
import com.github.pfichtner.refactoring.SourceUnit;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ExtractMethodTool}.
 */
public final class ExtractMethodTool {

    static SyncToolSpecification extractMethod() {
        Options opts = Options.of(FILE, START_LINE, START_COLUMN, END_LINE, END_COLUMN, METHOD_NAME);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_method", opts.toSchema())
                        .description("""
                        Extract statements into a new method.
                        Provide start/end line+column (1-based).
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args      = opts.reader(request.arguments());
                        Path file     = args.getPath(FILE);
                        int startLine = args.getInt(START_LINE);
                        int startCol  = args.getInt(START_COLUMN);
                        int endLine   = args.getInt(END_LINE);
                        int endCol    = args.getInt(END_COLUMN);
                        String methodName = args.getString(METHOD_NAME);

                        String source  = Files.readString(file);
                        int selStart   = JdtRenamer.toOffset(source, startLine, startCol);
                        int selEnd     = JdtRenamer.toOffset(source, endLine, endCol);
                        String result  = JdtExtractor.extractMethod(
                                new SourceUnit(source, file.getFileName().toString()),
                                selStart, selEnd - selStart, methodName);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
