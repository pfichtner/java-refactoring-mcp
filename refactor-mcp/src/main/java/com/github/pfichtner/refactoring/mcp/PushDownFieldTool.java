package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FIELD;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatDryrun;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtPushDownField;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code PushDownFieldTool}.
 */
public final class PushDownFieldTool {

    static SyncToolSpecification pushDownField() {
        Options opts = Options.builder().add(DRY_RUN, LINE, COLUMN, FIELD, CLASS).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("push_down_field", opts.toSchema())
                        .description("Push a field down from a class to all its direct subclasses in the project. The field is removed from the superclass and added to every subclass found. Returns new source for the superclass and all modified subclasses. Applies by default; pass dryrun=true to preview instead.")
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args  = opts.reader(request.arguments());
                    Path root = args.getPath(PROJECT_ROOT);
                    SourceFile source = args.getContent(FILE, root);

                    int offset    = source.resolve(args.getLocator());

                    var changed = JdtPushDownField.pushDown(
                            ProjectDetector.detect(root), source.path(), offset);
                    return args.getBoolean(DRY_RUN)
                            ? ok(formatDryrun(changed))
                            : ok(commit(changedMap(changed)));
                }))
                .build();
    }
}
