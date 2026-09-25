package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.REMOVE_DECLARATION;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.util.Map;
import java.util.stream.Collectors;

import com.github.pfichtner.refactoring.JdtInlineMethod;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code InlineMethodTool}.
 */
public final class InlineMethodTool {

    static SyncToolSpecification inlineMethod() {
        Options opts = Options.builder().required(PROJECT_ROOT, FILE).optional(DRY_RUN, LINE, COLUMN, METHOD, CLASS, REMOVE_DECLARATION).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_method", opts.toSchema())
                        .description("""
                        Inline a method call at all call sites in the project.
                        Optionally removes the method declaration.
                        Returns new source for every changed file. Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args      = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);
                    boolean removeDel = args.getBoolean(REMOVE_DECLARATION);
                    int offset    = source.resolve(args.getLocator());
                    var changed = JdtInlineMethod.inlineMethod(
                            ProjectDetector.detect(
                                    args.getPath(PROJECT_ROOT)),
                            source.path(), offset, removeDel);
                    return args.getBoolean(DRY_RUN)
                            ? ok(changed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                                    .map(e -> "=== " + e.getKey().getFileName() + " ===\n"
                                            + e.getValue().stripTrailing() + "\n\n")
                                    .collect(Collectors.joining()).stripTrailing())
                            : ok(commit(changedMap(changed)));
                }))
                .build();
    }
}
