package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FIELD;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.NEW_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.TYPE;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatDryrun;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtRenamer;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for the rename refactoring ({@link JdtRenamer}).
 */
public final class RenameTool {

    static SyncToolSpecification rename() {
        Options opts = Options.builder()
                .required(PROJECT_ROOT, FILE, NEW_NAME)
                .optional(DRY_RUN, LINE, COLUMN, METHOD, FIELD, TYPE, CLASS)
                .build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("rename", opts.toSchema())
                        .description("""
                        Rename a symbol (method, field, type, parameter, or local variable)
                        and update every reference across the project.
                        Applies by default; pass dryrun=true to preview without writing.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args = opts.reader(request.arguments());
                    List<FileChange> changed = rename(args);
                    return args.getBoolean(DRY_RUN) 
                    		? ok(formatDryrun(changed)) 
            				: ok(commit(changed));
                }))
                .build();
    }

    static List<FileChange> rename(Options.Reader args) throws Exception {
        Path filePath  = args.getPath(FILE);
        Path root      = args.getPath(PROJECT_ROOT);
        SourceFile source = new SourceFile(filePath.isAbsolute()
                ? filePath
                : root.resolve(filePath));

        int offset = source.resolve(args.getLocator());

        return JdtRenamer.rename(ProjectDetector.detect(root), source.path(), offset,
                args.getString(NEW_NAME));
    }

}