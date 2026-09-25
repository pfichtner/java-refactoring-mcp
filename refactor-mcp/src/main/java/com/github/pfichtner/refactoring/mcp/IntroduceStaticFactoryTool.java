package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FACTORY_METHOD_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.MAKE_CONSTRUCTOR_PRIVATE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatDryrun;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtIntroduceStaticFactory;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code IntroduceStaticFactoryTool}.
 */
public final class IntroduceStaticFactoryTool {

    static SyncToolSpecification introduceStaticFactory() {
        Options opts = Options.builder().add(DRY_RUN, LINE, COLUMN, METHOD, MAKE_CONSTRUCTOR_PRIVATE).addRequired(PROJECT_ROOT, FILE, FACTORY_METHOD_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_static_factory", opts.toSchema())
                        .description("Introduce a public static factory method for a constructor and rewrite every new ClassName(...) call site in the project to use it. make_constructor_private (default false): change the constructor visibility to private. Returns a preview of all changed files. Applies by default; pass dryrun=true to preview instead.")
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args    = opts.reader(request.arguments());
                    Path root   = args.getPath(PROJECT_ROOT);
                    SourceFile source = args.getContent(FILE, root);
                    String name = args.getString(FACTORY_METHOD_NAME);
                    boolean makePrivate = args.getBoolean(MAKE_CONSTRUCTOR_PRIVATE);

                    int offset    = source.resolve(args.getLocator());

                    var changed = JdtIntroduceStaticFactory.introduceStaticFactory(
                            ProjectDetector.detect(root),
                            source.path(), offset, name, makePrivate);

                    return args.getBoolean(DRY_RUN)
                            ? ok(formatDryrun(changed))
                            : ok(commit(changedMap(changed)));
                }))
                .build();
    }
}
