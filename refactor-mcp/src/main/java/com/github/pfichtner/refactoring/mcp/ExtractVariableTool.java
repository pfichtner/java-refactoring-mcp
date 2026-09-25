package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.END_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.END_LINE;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.REPLACE_ALL;
import static com.github.pfichtner.refactoring.mcp.Property.START_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.START_LINE;
import static com.github.pfichtner.refactoring.mcp.Property.VAR_NAME;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

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
        Options opts = Options.builder().required(FILE, START_LINE, START_COLUMN, END_LINE, END_COLUMN, VAR_NAME).optional(DRY_RUN, REPLACE_ALL).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_variable", opts.toSchema())
                        .description("""
                        Extract an expression into a new local variable.
                        Returns the rewritten source. Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
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
                    return args.getBoolean(DRY_RUN)
                            ? ok(result)
                            : ok(commit(List.of(overwrite(source.path(), result))));
                }))
                .build();
    }
}
