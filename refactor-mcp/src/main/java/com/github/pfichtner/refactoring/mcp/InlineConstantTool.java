package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FIELD;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.REMOVE_DECLARATION;
import static com.github.pfichtner.refactoring.mcp.Property.REPLACE_ALL;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

import com.github.pfichtner.refactoring.JdtInliner;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code InlineConstantTool}.
 */
public final class InlineConstantTool {

    static SyncToolSpecification inlineConstant() {
        Options opts = Options.builder().required(FILE).optional(DRY_RUN, LINE, COLUMN, FIELD, CLASS, REPLACE_ALL, REMOVE_DECLARATION).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_constant", opts.toSchema())
                        .description("""
                        Inline a static final constant: replace one or all references with its
                        initializer expression, and optionally remove the field declaration.
                        Returns the rewritten source. Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args       = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);
                    boolean allOcc = args.getBoolean(REPLACE_ALL);
                    boolean removeDecl = args.getBoolean(REMOVE_DECLARATION);

                    int offset    = source.resolve(args.getLocator());
                    String result = JdtInliner.inlineConstant(
                            source.content(), source.path().getFileName().toString(), offset, allOcc, removeDecl);
                    return args.getBoolean(DRY_RUN)
                            ? ok(result)
                            : ok(commit(List.of(overwrite(source.path(), result))));
                }))
                .build();
    }
}
