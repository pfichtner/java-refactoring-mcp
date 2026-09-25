package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.REPLACE_ALL;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

import com.github.pfichtner.refactoring.JdtConvertToStaticImport;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for the convert-to-static-import refactoring
 * ({@link JdtConvertToStaticImport}).
 */
public final class ConvertToStaticImportTool {

    static SyncToolSpecification convertToStaticImport() {
        Options opts = Options.builder()
                .required(FILE)
                .optional(DRY_RUN, LINE, COLUMN, METHOD, REPLACE_ALL)
                .build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_to_static_import", opts.toSchema())
                        .description("""
                        Convert a qualified static-method call (e.g. Collectors.joining(...))
                        to use a static import instead.
                        Adds 'import static fully.qualified.Class.method;' and removes the qualifier.
                        If the static import already exists, only the qualifier is removed.
                        Pass replace_all=true to convert every qualifying call to the same method in the file.
                        Applies by default; pass dryrun=true to preview without writing.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args      = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);
                    boolean replaceAll = args.getBoolean(REPLACE_ALL);
                    int offset = source.resolve(args.getLocator());
                    String result = JdtConvertToStaticImport.convertToStaticImport(
                            source.content(), source.path().getFileName().toString(),
                            offset, replaceAll);
                    return args.getBoolean(DRY_RUN)
                            ? ok(result)
                            : ok(commit(List.of(overwrite(source.path(), result))));
                }))
                .build();
    }
}
