package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FIELD;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.GENERATE_SETTER;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatDryrun;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtEncapsulateField;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code EncapsulateFieldTool}.
 */
public final class EncapsulateFieldTool {

    static SyncToolSpecification encapsulateField() {
        Options opts = Options.builder().required(PROJECT_ROOT, FILE).optional(DRY_RUN, LINE, COLUMN, FIELD, CLASS, GENERATE_SETTER).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("encapsulate_field", opts.toSchema())
                        .description("""
                        Make a public field private, generate a getter (and optional setter),
                        and rewrite all read access sites (and write sites if generate_setter=true)
                        across the project. Returns changed file contents. Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args  = opts.reader(request.arguments());
                    Path root = args.getPath(PROJECT_ROOT);
                    SourceFile source = args.getContent(FILE, root);
                    int offset = source.resolve(args.getLocator());
                    boolean generateSetter = args.getBoolean(GENERATE_SETTER);
                    var changed = JdtEncapsulateField.encapsulateField(
                            ProjectDetector.detect(root), source.path(), offset, generateSetter);
                    return args.getBoolean(DRY_RUN)
                            ? ok(formatDryrun(changed))
                            : ok(commit(changedMap(changed)));
                }))
                .build();
    }
}
