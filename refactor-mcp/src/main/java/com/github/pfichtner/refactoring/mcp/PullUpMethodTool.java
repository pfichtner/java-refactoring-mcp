package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.WIDEN_VISIBILITY;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.util.Map;
import java.util.stream.Collectors;

import com.github.pfichtner.refactoring.JdtPullUpMethod;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code PullUpMethodTool}.
 */
public final class PullUpMethodTool {

    static SyncToolSpecification pullUpMethod() {
        Options opts = Options.builder().add(DRY_RUN, LINE, COLUMN, METHOD, CLASS, WIDEN_VISIBILITY).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("pull_up_method", opts.toSchema())
                        .description("""
                        Pull a method up from a subclass to its direct superclass.
                        The superclass must be in the project source roots.
                        Returns new source for both the subclass and the superclass.
                        Applies by default; pass dryrun=true to preview instead.
                        widen_visibility (default true): if the method is private, changes it to protected in the superclass.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        int offset    = source.resolve(args.getLocator());
                        boolean widen = args.getBoolean(WIDEN_VISIBILITY, true);
                        var changed   = JdtPullUpMethod.pullUp(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                source.path(), offset, widen);
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
