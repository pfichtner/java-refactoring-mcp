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
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.joining;

import java.util.Map;

import com.github.pfichtner.refactoring.JdtPullUpMethod;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code PullUpMethodTool}.
 */
public final class PullUpMethodTool {

    static SyncToolSpecification pullUpMethod() {
        Options opts = Options.builder().required(PROJECT_ROOT, FILE).optional(DRY_RUN, LINE, COLUMN, METHOD, CLASS, WIDEN_VISIBILITY).build();
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
                .callHandler((exchange, request) -> execute(() -> {
                    var args   = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);
                    int offset    = source.resolve(args.getLocator());
                    boolean widen = args.getBoolean(WIDEN_VISIBILITY, true);
                    var changed   = JdtPullUpMethod.pullUp(
                            ProjectDetector.detect(
                                    args.getPath(PROJECT_ROOT)),
                            source.path(), offset, widen);
                    return args.getBoolean(DRY_RUN)
                            ? ok(changed.entrySet().stream().sorted(comparingByKey())
                                    .map(e -> "=== " + e.getKey().getFileName() + " ===\n"
                                            + e.getValue().stripTrailing() + "\n\n")
                                    .collect(joining()).stripTrailing())
                            : ok(commit(changedMap(changed)));
                }))
                .build();
    }
}
