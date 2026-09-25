package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.INDIRECTION_METHOD_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

import com.github.pfichtner.refactoring.JdtIntroduceIndirection;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code IntroduceIndirectionTool}.
 */
public final class IntroduceIndirectionTool {

    static SyncToolSpecification introduceIndirection() {
        Options opts = Options.builder().add(DRY_RUN).addRequired(FILE, LINE, COLUMN, INDIRECTION_METHOD_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_indirection", opts.toSchema())
                .description("Add a public static indirection (wrapper) method that delegates to the method at the given position. Applies by default; pass dryrun=true to preview instead.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args    = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        int offset  = source.resolve(args.getLocator());
                        String name = args.getString(INDIRECTION_METHOD_NAME);
                        String result = JdtIntroduceIndirection.introduceIndirection(
                                source.content(), source.path().getFileName().toString(), offset, name);
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
