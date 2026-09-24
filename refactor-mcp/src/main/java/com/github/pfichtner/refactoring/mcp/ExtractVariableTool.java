package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.END_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.END_LINE;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.REPLACE_ALL;
import static com.github.pfichtner.refactoring.mcp.Property.START_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.START_LINE;
import static com.github.pfichtner.refactoring.mcp.Property.VAR_NAME;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import com.github.pfichtner.refactoring.JdtExtractVariable;
import com.github.pfichtner.refactoring.JdtRenamer;
import com.github.pfichtner.refactoring.SourceUnit;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ExtractVariableTool}.
 */
public final class ExtractVariableTool {

    static SyncToolSpecification extractVariable() {
        Options opts = Options.builder().add(REPLACE_ALL).addRequired(FILE, START_LINE, START_COLUMN, END_LINE, END_COLUMN, VAR_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_variable", opts.toSchema())
                        .description("""
                        Extract an expression into a new local variable.
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args      = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        int startLine = args.getInt(START_LINE);
                        int startCol  = args.getInt(START_COLUMN);
                        int endLine   = args.getInt(END_LINE);
                        int endCol    = args.getInt(END_COLUMN);
                        String varName    = args.getString(VAR_NAME);
                        boolean replaceAll = args.getBoolean(REPLACE_ALL);

                        int selStart   = JdtRenamer.toOffset(source.content(), startLine, startCol);
                        int selEnd     = JdtRenamer.toOffset(source.content(), endLine, endCol);
                        String result  = JdtExtractVariable.extractVariable(
                                new SourceUnit(source.content(), source.path().getFileName().toString()),
                                selStart, selEnd - selStart, varName, replaceAll);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
