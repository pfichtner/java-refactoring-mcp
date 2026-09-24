package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatPreview;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtConvertToRecord;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ConvertToRecordTool}.
 */
public final class ConvertToRecordTool {

    static SyncToolSpecification convertToRecord() {
        Options opts = Options.of(PROJECT_ROOT, FILE);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_to_record", opts.toSchema())
                        .description("Convert a class to a Java record and rewrite accessor call sites across the project. Returns new source for each changed file; does not write to disk.")
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args  = opts.reader(request.arguments());
                        Path root = args.getPath(PROJECT_ROOT);
                        Path file = root.resolve(args.getPath(FILE));
                        var changed = JdtConvertToRecord.convertToRecord(
                                ProjectDetector.detect(root), file);
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
