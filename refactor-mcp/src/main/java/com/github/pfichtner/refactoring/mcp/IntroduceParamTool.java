package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.END_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.END_LINE;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_TYPE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.START_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.START_LINE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.util.Map;
import java.util.stream.Collectors;

import com.github.pfichtner.refactoring.JdtIntroduceParam;
import com.github.pfichtner.refactoring.JdtRenamer;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code IntroduceParamTool}.
 */
public final class IntroduceParamTool {

    static SyncToolSpecification introduceParam() {
        Options opts = Options.builder().required(PROJECT_ROOT, FILE, START_LINE, START_COLUMN, END_LINE, END_COLUMN, PARAM_NAME).optional(DRY_RUN, PARAM_TYPE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_param", opts.toSchema())
                        .description("""
                        Promote an expression to a parameter.
                        Updates the method signature and all call sites in the project.
                        Returns a map of filename → new source for each changed file. Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args      = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);
                    int startLine = args.getInt(START_LINE);
                    int startCol  = args.getInt(START_COLUMN);
                    int endLine   = args.getInt(END_LINE);
                    int endCol    = args.getInt(END_COLUMN);
                    String paramName = args.getString(PARAM_NAME);
                    String paramType = args.getString(PARAM_TYPE); // nullable

                    int selStart   = JdtRenamer.toOffset(source.content(), startLine, startCol);
                    int selEnd     = JdtRenamer.toOffset(source.content(), endLine, endCol);

                    var changed = JdtIntroduceParam.introduceParam(
                            ProjectDetector.detect(args.getPath(PROJECT_ROOT)),
                            source.path(), selStart, selEnd - selStart, paramName, paramType);

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
