package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD_NAMES;
import static com.github.pfichtner.refactoring.mcp.Property.SUPERCLASS_NAME;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtExtractSuperclass;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ExtractSuperclassTool}.
 */
public final class ExtractSuperclassTool {

    static SyncToolSpecification extractSuperclass() {
        Options opts = Options.builder().add(DRY_RUN, METHOD_NAMES).addRequired(FILE, SUPERCLASS_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_superclass", opts.toSchema())
                        .description("""
                        Extract an abstract superclass from selected methods of a class.
                        Returns the modified class source and the new superclass source.
                        Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = opts.reader(request.arguments());
                        SourceFile source = args.getContent(FILE);
                        String superName     = args.getString(SUPERCLASS_NAME);
                        List<String> methods = args.getStringList(METHOD_NAMES);
                        var result = JdtExtractSuperclass.extractSuperclass(
                                source.content(), source.path().getFileName().toString(), superName, methods);

                        return args.getBoolean(DRY_RUN)
                                ? ok("=== " + source.path().getFileName() + " (modified) ===\n"
                                        + result.modifiedClassSource().stripTrailing() + "\n\n"
                                        + "=== " + superName + ".java (new) ===\n"
                                        + result.superclassSource().stripTrailing())
                                : ok(commit(List.of(
                                        overwrite(source.path(), result.modifiedClassSource()),
                                        overwrite(source.path().getParent()
                                                .resolve(superName + ".java"), result.superclassSource()))));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
