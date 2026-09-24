package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.pfichtner.refactoring.JdtPushDownMethod;
import com.github.pfichtner.refactoring.locator.LocatorResolver;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code PushDownMethodTool}.
 */
public final class PushDownMethodTool {

    static SyncToolSpecification pushDownMethod() {
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("push_down_method", opts.toSchema())
                        .description("""
                        Push a method down from a class to all its direct subclasses in the project.
                        The method is removed from the superclass and added to every subclass found.
                        Subclasses are discovered by scanning for 'extends ClassName' in source files.
                        Returns new source for the superclass and all modified subclasses.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        Path file   = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset    = LocatorResolver.resolve(args.getLocator(), source, file.getFileName().toString());
                        var changed   = JdtPushDownMethod.pushDown(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                file, offset);
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
