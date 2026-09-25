package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.CASCADE;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.util.Map;
import java.util.stream.Collectors;

import com.github.pfichtner.refactoring.JdtRemoveMethod;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code RemoveMethodTool}.
 */
public final class RemoveMethodTool {

    static SyncToolSpecification removeMethod() {
        Options opts = Options.builder().required(PROJECT_ROOT, FILE).optional(DRY_RUN, LINE, COLUMN, METHOD, CLASS, CASCADE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("remove_method", opts.toSchema())
                        .description("""
                        Remove a method from a class or interface.
                        When cascade is true (the default), also removes every overriding method
                        in subclasses and implementing method in implementing classes.
                        Returns a map of filename → new source for each changed file. Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args    = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);
                    int offset    = source.resolve(args.getLocator());
                    boolean cascade = args.getBoolean(CASCADE, true);
                    var changed = JdtRemoveMethod.removeMethod(
                            ProjectDetector.detect(args.getPath(PROJECT_ROOT)),
                            source.path(), offset, cascade);
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
