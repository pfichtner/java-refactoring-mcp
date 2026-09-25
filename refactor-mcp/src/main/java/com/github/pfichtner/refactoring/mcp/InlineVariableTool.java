package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.VARIABLE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

import com.github.pfichtner.refactoring.JdtInliner;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code InlineVariableTool}.
 */
public final class InlineVariableTool {

    static SyncToolSpecification inlineVariable() {
        Options opts = Options.builder().required(FILE).optional(DRY_RUN, LINE, COLUMN, VARIABLE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_variable", opts.toSchema())
                        .description("""
                        Inline a local variable: replace every use with its initializer expression
                        and remove the declaration. Returns the rewritten source. Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args   = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);

                    int offset    = source.resolve(args.getLocator());
                    String result = JdtInliner.inlineVariable(
                            source.content(), source.path().getFileName().toString(), offset);
                    return args.getBoolean(DRY_RUN)
                            ? ok(result)
                            : ok(commit(List.of(overwrite(source.path(), result))));
                }))
                .build();
    }
}
