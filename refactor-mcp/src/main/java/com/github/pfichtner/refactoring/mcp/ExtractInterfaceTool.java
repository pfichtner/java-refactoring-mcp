package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.INTERFACE_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD_NAMES;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.overwrite;

import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtExtractInterface;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ExtractInterfaceTool}.
 */
public final class ExtractInterfaceTool {

    static SyncToolSpecification extractInterface() {
        Options opts = Options.builder().add(DRY_RUN, METHOD_NAMES).addRequired(FILE, INTERFACE_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_interface", opts.toSchema())
                        .description("""
                        Extract a new interface from the public methods of a class.
                        Returns the modified class source and the new interface source.
                        Applies by default; pass dryrun=true to preview instead.
                        """)
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args = opts.reader(request.arguments());
                    SourceFile source = args.getContent(FILE);
                    String interfaceName  = args.getString(INTERFACE_NAME);
                    List<String> methods  = args.getStringList(METHOD_NAMES);
                    var result = JdtExtractInterface.extractInterface(
                            source.content(), source.path().getFileName().toString(),
                            interfaceName, methods);

                    return args.getBoolean(DRY_RUN)
                            ? ok("=== " + source.path().getFileName() + " (modified) ===\n"
                                    + result.modifiedClassSource().stripTrailing() + "\n\n"
                                    + "=== " + interfaceName + ".java (new) ===\n"
                                    + result.interfaceSource().stripTrailing())
                            : ok(commit(List.of(
                                    overwrite(source.path(), result.modifiedClassSource()),
                                    overwrite(source.path().getParent()
                                            .resolve(interfaceName + ".java"), result.interfaceSource()))));
                }))
                .build();
    }
}
