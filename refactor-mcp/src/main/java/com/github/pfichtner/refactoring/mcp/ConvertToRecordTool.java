package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatDryrun;
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
        Options opts = Options.builder().add(DRY_RUN).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_to_record", opts.toSchema())
                        .description("Convert a class to a Java record and rewrite accessor call sites across the project. Returns new source for each changed file. Applies by default; pass dryrun=true to preview instead.")
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args  = opts.reader(request.arguments());
                    Path root = args.getPath(PROJECT_ROOT);
                    Path file = root.resolve(args.getPath(FILE));
                    var changed = JdtConvertToRecord.convertToRecord(
                            ProjectDetector.detect(root), file);
                    return args.getBoolean(DRY_RUN)
                            ? ok(formatDryrun(changed))
                            : ok(commit(changedMap(changed)));
                }))
                .build();
    }
}
