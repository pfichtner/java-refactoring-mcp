package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.MAX_CODE_GROWTH_CHARS;
import static com.github.pfichtner.refactoring.mcp.Property.MAX_TRANSFORMATIONS;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.REMOVE_DECLARATION;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.joining;

import com.github.pfichtner.refactoring.inlining.InlineConfig;
import com.github.pfichtner.refactoring.inlining.InliningEngine;
import com.github.pfichtner.refactoring.inlining.ProjectInliningResult;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for aggressive project-wide inlining via {@link InliningEngine}.
 */
public final class InlineAggressiveTool {

    static SyncToolSpecification inlineAggressive() {
        Options opts = Options.builder()
                .required(PROJECT_ROOT)
                .optional(DRY_RUN, MAX_TRANSFORMATIONS, MAX_CODE_GROWTH_CHARS, REMOVE_DECLARATION)
                .build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_aggressive", opts.toSchema())
                        .description("""
                        Aggressively inline all inlineable calls project-wide to a fixed point.
                        Applies multiple rounds of inlining (method calls, getters, constants,
                        final fields, delegating constructors, delegation) until no further
                        transformations apply or a budget is reached.
                        Returns new source for every changed file. Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args = opts.reader(request.arguments());
                    int maxT = args.has(MAX_TRANSFORMATIONS) ? args.getInt(MAX_TRANSFORMATIONS) : 100;
                    int maxG = args.has(MAX_CODE_GROWTH_CHARS) ? args.getInt(MAX_CODE_GROWTH_CHARS) : 100_000;
                    boolean removeUnused = args.getBoolean(REMOVE_DECLARATION);
                    InlineConfig config = new InlineConfig(maxT, maxG, removeUnused);
                    ProjectInliningResult result = InliningEngine.inlineProject(
                            ProjectDetector.detect(args.getPath(PROJECT_ROOT)), config);
                    String stats = "Applied " + result.transformationCount() + " transformation(s)"
                            + (result.fixedPoint() ? ", fixed point." : ", budget exhausted.");
                    if (args.getBoolean(DRY_RUN)) {
                        String files = result.changedFiles().entrySet().stream()
                                .sorted(comparingByKey())
                                .map(e -> "=== " + e.getKey().getFileName() + " ===\n"
                                        + e.getValue().stripTrailing() + "\n\n")
                                .collect(joining()).stripTrailing();
                        return ok(stats + (files.isEmpty() ? "" : "\n\n" + files));
                    }
                    return ok(stats + "\n" + commit(changedMap(result.changedFiles())));
                }))
                .build();
    }
}
