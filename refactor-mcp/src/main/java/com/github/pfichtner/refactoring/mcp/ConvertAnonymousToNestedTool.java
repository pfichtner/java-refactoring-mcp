package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.NESTED_CLASS_NAME;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

import com.github.pfichtner.refactoring.JdtConvertAnonymousToNested;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ConvertAnonymousToNestedTool}.
 */
public final class ConvertAnonymousToNestedTool {

    static SyncToolSpecification convertAnonymousToNested() {
        Options opts = Options.builder().add(DRY_RUN).addRequired(FILE, LINE, COLUMN, NESTED_CLASS_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_anonymous_to_nested", opts.toSchema())
                .description("Convert an anonymous class at the given position to a private named nested class. Applies by default; pass dryrun=true to preview instead.")
                .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args          = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);
                    int offset        = source.resolve(args.getLocator());
                    String nestedName = args.getString(NESTED_CLASS_NAME);
                    String result     = JdtConvertAnonymousToNested.convert(
                            source.content(), source.path().getFileName().toString(), offset, nestedName);
                    return args.getBoolean(DRY_RUN)
                            ? ok(result)
                            : ok(commit(List.of(overwrite(source.path(), result))));
                }))
                .build();
    }
}
