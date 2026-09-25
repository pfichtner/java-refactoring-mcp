package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.NEW_RETURN_TYPE;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_ORDER;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_TYPES;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatDryrun;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtChangeMethodSignature;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ChangeMethodSignatureTool}.
 */
public final class ChangeMethodSignatureTool {

    static SyncToolSpecification changeMethodSignature() {
        Options opts = Options.builder().required(PROJECT_ROOT, FILE).optional(DRY_RUN, LINE, COLUMN, METHOD, CLASS, NEW_RETURN_TYPE, PARAM_ORDER, PARAM_TYPES).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("change_method_signature", opts.toSchema())
                        .description("""
                        Change a method's return type, reorder its parameters, and/or change parameter types — declaration-only edits except param_order which also updates call sites project-wide.
                        Axes (at least one required):
                          new_return_type: new return type text (e.g. "double") — declaration only.
                          param_order: integer array where param_order[i] is the original index of the parameter that should appear at position i — call sites updated.
                          param_types: parallel string array where param_types[i] is the new type for parameter i (null/empty = leave unchanged) — declaration only.
                        Does NOT add or remove parameters; use introduce_param to add and remove_param to drop one.
                        Returns changed file contents. Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args  = opts.reader(request.arguments());
                    Path root = args.getPath(PROJECT_ROOT);
                    SourceFile source = args.getContent(FILE, root);
                    int offset = source.resolve(args.getLocator());
                    String newReturnType = args.getString(NEW_RETURN_TYPE);
                    int[]    paramOrder = args.getIntArray(PARAM_ORDER);
                    String[] paramTypes = args.getStringArray(PARAM_TYPES);
                    var changed = JdtChangeMethodSignature.changeSignature(
                            ProjectDetector.detect(root), source.path(), offset, newReturnType, paramOrder, paramTypes);
                    return args.getBoolean(DRY_RUN)
                            ? ok(formatDryrun(changed))
                            : ok(commit(changedMap(changed)));
                }))
                .build();
    }
}
