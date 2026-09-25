package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.util.Map;
import java.util.stream.Collectors;

import com.github.pfichtner.refactoring.JdtPushDownMethod;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code PushDownMethodTool}.
 */
public final class PushDownMethodTool {

    static SyncToolSpecification pushDownMethod() {
        Options opts = Options.builder().add(DRY_RUN, LINE, COLUMN, METHOD, CLASS).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("push_down_method", opts.toSchema())
                        .description("""
                        Push a method down from a class to all its direct subclasses in the project.
                        The method is removed from the superclass and added to every subclass found.
                        Subclasses are discovered by scanning for 'extends ClassName' in source files.
                        Returns new source for the superclass and all modified subclasses.
                        Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        int offset    = source.resolve(args.getLocator());
                        var changed   = JdtPushDownMethod.pushDown(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                source.path(), offset);
                        if (args.getBoolean(DRY_RUN)) {
                            return ok(changed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                                    .map(e -> "=== " + e.getKey().getFileName() + " ===\n"
                                            + e.getValue().stripTrailing() + "\n\n")
                                    .collect(Collectors.joining()).stripTrailing());
                        }
                        return ok(commit(changedMap(changed)));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
