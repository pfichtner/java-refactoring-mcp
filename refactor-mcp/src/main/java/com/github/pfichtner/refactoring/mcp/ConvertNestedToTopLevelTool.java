package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtConvertNestedToTopLevel;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ConvertNestedToTopLevelTool}.
 */
public final class ConvertNestedToTopLevelTool {

    static SyncToolSpecification convertNestedToTopLevel() {
        Options opts = Options.builder().add(DRY_RUN, LINE, COLUMN).addRequired(FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_nested_to_top_level", opts.toSchema())
                .description("Convert a nested (member) type to a top-level type. Returns both the modified outer source and the new type's source. Applies by default; pass dryrun=true to preview instead.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        int offset = source.resolve(args.getLocator());
                        JdtConvertNestedToTopLevel.Result result =
                                JdtConvertNestedToTopLevel.convert(
                                        source.content(), source.path().getFileName().toString(), offset);

                        return args.getBoolean(DRY_RUN)
                                ? ok("=== " + source.path().getFileName() + " (modified) ===\n"
                                        + result.outerSource()
                                        + "\n=== " + result.newTypeName() + ".java (new file) ===\n"
                                        + result.newTypeSource())
                                : ok(commit(List.of(
                                        overwrite(source.path(), result.outerSource()),
                                        overwrite(source.path().getParent()
                                                .resolve(result.newTypeName() + ".java"), result.newTypeSource()))));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
