package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.TARGET_CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.WIDEN_VISIBILITY;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.util.Map;
import java.util.stream.Collectors;

import com.github.pfichtner.refactoring.JdtMoveMethod;
import com.github.pfichtner.refactoring.locator.LocatorResolver;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code MoveMethodTool}.
 */
public final class MoveMethodTool {

    static SyncToolSpecification moveMethod() {
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS, WIDEN_VISIBILITY).addRequired(PROJECT_ROOT, FILE, TARGET_CLASS).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_method", opts.toSchema())
                        .description("""
                        Move a method from one class to another class within the project.
                        The method is removed from the source class and added to the target class.
                        Target class is identified by its fully-qualified name (e.g. com.example.Report).
                        Call sites in other files are not updated.
                        Returns new source for both the target file and the source file.
                        Does not write to disk.
                        widen_visibility (default true): if the method is private, widens to package-private (same package) or public (cross-package).
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args         = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        String targetClass = args.getString(TARGET_CLASS);
                        int offset        = LocatorResolver.resolve(args.getLocator(), source.content(), source.path().getFileName().toString());
                        boolean widen     = args.getBoolean(WIDEN_VISIBILITY, true);
                        var changed = JdtMoveMethod.moveMethod(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                source.path(), offset, targetClass, widen);
                        return ok(changed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                                .map(e -> "=== " + e.getKey().getFileName() + " ===\n"
                                        + e.getValue().stripTrailing() + "\n\n")
                                .collect(Collectors.joining()).stripTrailing());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
