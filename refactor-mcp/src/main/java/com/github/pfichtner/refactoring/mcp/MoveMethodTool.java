package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.TARGET_CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.WIDEN_VISIBILITY;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.joining;

import java.util.Map;

import com.github.pfichtner.refactoring.JdtMoveMethod;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code MoveMethodTool}.
 */
public final class MoveMethodTool {

    static SyncToolSpecification moveMethod() {
        Options opts = Options.builder().required(PROJECT_ROOT, FILE, TARGET_CLASS).optional(DRY_RUN, LINE, COLUMN, METHOD, CLASS, WIDEN_VISIBILITY).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_method", opts.toSchema())
                        .description("""
                        Move a method from one class to another class within the project.
                        The method is removed from the source class and added to the target class.
                        Target class is identified by its fully-qualified name (e.g. com.example.Report).
                        Call sites in other files are not updated.
                        Returns new source for both the target file and the source file.
                        Applies by default; pass dryrun=true to preview instead.
                        widen_visibility (default true): if the method is private, widens to package-private (same package) or public (cross-package).
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args         = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);
                    String targetClass = args.getString(TARGET_CLASS);
                    int offset        = source.resolve(args.getLocator());
                    boolean widen     = args.getBoolean(WIDEN_VISIBILITY, true);
                    var changed = JdtMoveMethod.moveMethod(
                            ProjectDetector.detect(
                                    args.getPath(PROJECT_ROOT)),
                            source.path(), offset, targetClass, widen);
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
