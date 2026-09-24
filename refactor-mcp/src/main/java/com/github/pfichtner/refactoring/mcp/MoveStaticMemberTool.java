package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.TARGET_CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.WIDEN_VISIBILITY;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Files;
import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtMoveStaticMember;
import com.github.pfichtner.refactoring.locator.LocatorResolver;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code MoveStaticMemberTool}.
 */
public final class MoveStaticMemberTool {

    static SyncToolSpecification moveStaticMember() {
        Options opts = Options.builder().add(WIDEN_VISIBILITY).addRequired(PROJECT_ROOT, FILE, LINE, COLUMN, TARGET_CLASS).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_static_member", opts.toSchema())
                .description("Move a static method or static field to another class and update call sites. widen_visibility (default true): if the member is private, widens to package-private (same package) or public (cross-package).")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args          = opts.reader(request.arguments());
                        Path projectRoot  = args.getPath(PROJECT_ROOT);
                        Path file         = args.getPath(FILE);
                        String source     = Files.readString(file);
                        int offset        = LocatorResolver.resolve(args.getLocator(), source, file.getFileName().toString());
                        String target     = args.getString(TARGET_CLASS);
                        boolean widen     = args.getBoolean(WIDEN_VISIBILITY, true);
                        var project = new com.github.pfichtner.refactoring.project.MavenProject(projectRoot);
                        java.util.Map<java.nio.file.Path, String> changed =
                                JdtMoveStaticMember.moveStaticMember(project, file, offset, target, widen);
                        StringBuilder sb = new StringBuilder();
                        changed.forEach((p, src) ->
                                sb.append("=== ").append(p.getFileName()).append(" ===\n").append(src).append("\n"));
                        return ok(sb.toString());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
