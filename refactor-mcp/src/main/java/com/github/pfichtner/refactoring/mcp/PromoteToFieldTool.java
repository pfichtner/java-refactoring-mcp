package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

import com.github.pfichtner.refactoring.JdtPromoteToField;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code PromoteToFieldTool}.
 */
public final class PromoteToFieldTool {

    static SyncToolSpecification promoteToField() {
        Options opts = Options.builder().add(DRY_RUN).addRequired(FILE, LINE, COLUMN).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("promote_to_field", opts.toSchema())
                .description("Promote a local variable declaration to a private instance field of the enclosing class. Applies by default; pass dryrun=true to preview instead.")
                .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args    = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);
                    int offset  = source.resolve(args.getLocator());
                    String result = JdtPromoteToField.promote(
                            source.content(), source.path().getFileName().toString(), offset);
                    return args.getBoolean(DRY_RUN)
                            ? ok(result)
                            : ok(commit(List.of(overwrite(source.path(), result))));
                }))
                .build();
    }
}
