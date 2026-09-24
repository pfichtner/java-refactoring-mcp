package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FIELD;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.WIDEN_VISIBILITY;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatPreview;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtPullUpField;
import com.github.pfichtner.refactoring.locator.LocatorResolver;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code PullUpFieldTool}.
 */
public final class PullUpFieldTool {

    static SyncToolSpecification pullUpField() {
        Options opts = Options.builder().add(LINE, COLUMN, FIELD, CLASS, WIDEN_VISIBILITY).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("pull_up_field", opts.toSchema())
                        .description("Pull a field up from a subclass to its direct superclass. widen_visibility (default true): if the field is private, changes it to protected in the superclass.")
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args  = opts.reader(request.arguments());
                        Path root = args.getPath(PROJECT_ROOT);
                        SourceFile source = args.getContent(FILE, root);

                        int offset    = LocatorResolver.resolve(args.getLocator(), source.content(), source.path().getFileName().toString());
                        boolean widen = args.getBoolean(WIDEN_VISIBILITY, true);

                        var changed = JdtPullUpField.pullUp(
                                ProjectDetector.detect(root), source.path(), offset, widen);
                        return ok(formatPreview(changed));
                    } catch (IllegalArgumentException e) {
                        return error(e.getMessage());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
