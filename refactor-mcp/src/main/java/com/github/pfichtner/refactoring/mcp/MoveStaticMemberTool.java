package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.TARGET_CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.WIDEN_VISIBILITY;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Path;
import java.util.stream.Collectors;

import com.github.pfichtner.refactoring.JdtMoveStaticMember;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code MoveStaticMemberTool}.
 */
public final class MoveStaticMemberTool {

    static SyncToolSpecification moveStaticMember() {
        Options opts = Options.builder().add(DRY_RUN, WIDEN_VISIBILITY).addRequired(PROJECT_ROOT, FILE, LINE, COLUMN, TARGET_CLASS).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_static_member", opts.toSchema())
                .description("Move a static method or static field to another class and update call sites. widen_visibility (default true): if the member is private, widens to package-private (same package) or public (cross-package). Applies by default; pass dryrun=true to preview instead.")
                .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args          = opts.reader(request.arguments());
                    Path projectRoot  = args.getPath(PROJECT_ROOT);
                    SourceFile source = args.getContent(FILE);
                    int offset        = source.resolve(args.getLocator());
                    String target     = args.getString(TARGET_CLASS);
                    boolean widen     = args.getBoolean(WIDEN_VISIBILITY, true);
                    var project = ProjectDetector.detect(projectRoot);
						var changed = JdtMoveStaticMember.moveStaticMember(project, source.path(), offset, target, widen);
                    return args.getBoolean(DRY_RUN)
                            ? ok(changed.entrySet().stream()
                                    .map(e -> "=== " + e.getKey().getFileName() + " ===\n" + e.getValue() + "\n")
                                    .collect(Collectors.joining()))
                            : ok(commit(changedMap(changed)));
                }))
                .build();
    }
}
