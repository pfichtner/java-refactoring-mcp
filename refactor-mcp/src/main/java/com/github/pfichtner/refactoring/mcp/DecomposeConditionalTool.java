package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD_NAME;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

import com.github.pfichtner.refactoring.JdtDecomposeConditional;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code DecomposeConditionalTool}.
 */
public final class DecomposeConditionalTool {

    static SyncToolSpecification decomposeConditional() {
        Options opts = Options.builder().add(DRY_RUN, LINE, COLUMN).addRequired(FILE, METHOD_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("decompose_conditional", opts.toSchema())
                        .description("""
                        Extract a compound boolean condition from an if/while/for statement
                        into a private boolean method. Single-file operation.
                        Returns the modified source. Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args      = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        String methodName = args.getString(METHOD_NAME);
                        int offset    = source.resolve(args.getLocator());
                        String result = JdtDecomposeConditional.decomposeConditional(
                                source.content(), source.path().getFileName().toString(), offset, methodName);
                        return args.getBoolean(DRY_RUN)
                                ? ok(result)
                                : ok(commit(List.of(overwrite(source.path(), result))));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
