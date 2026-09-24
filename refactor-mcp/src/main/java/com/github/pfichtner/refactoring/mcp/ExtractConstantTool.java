package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.CONST_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.END_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.END_LINE;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.REPLACE_ALL;
import static com.github.pfichtner.refactoring.mcp.Property.START_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.START_LINE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import com.github.pfichtner.refactoring.JdtExtractConstant;
import com.github.pfichtner.refactoring.JdtRenamer;
import com.github.pfichtner.refactoring.SourceUnit;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ExtractConstantTool}.
 */
public final class ExtractConstantTool {

    static SyncToolSpecification extractConstant() {
        Options opts = Options.builder().add(REPLACE_ALL).addRequired(FILE, START_LINE, START_COLUMN, END_LINE, END_COLUMN, CONST_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_constant", opts.toSchema())
                        .description("""
                        Extract an expression into a private static final constant at class level.
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args       = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        int startLine  = args.getInt(START_LINE);
                        int startCol   = args.getInt(START_COLUMN);
                        int endLine    = args.getInt(END_LINE);
                        int endCol     = args.getInt(END_COLUMN);
                        String constName  = args.getString(CONST_NAME);
                        boolean replaceAll = args.getBoolean(REPLACE_ALL);

                        int selStart   = JdtRenamer.toOffset(source.content(), startLine, startCol);
                        int selEnd     = JdtRenamer.toOffset(source.content(), endLine, endCol);
                        return ok(JdtExtractConstant.extractConstant(
                                new SourceUnit(source.content(), source.path().getFileName().toString()),
                                selStart, selEnd - selStart, constName, replaceAll));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
