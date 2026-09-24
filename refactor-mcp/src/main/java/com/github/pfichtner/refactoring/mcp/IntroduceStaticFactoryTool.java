package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FACTORY_METHOD_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.MAKE_CONSTRUCTOR_PRIVATE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.error;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatPreview;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Files;
import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtIntroduceStaticFactory;
import com.github.pfichtner.refactoring.locator.LocatorResolver;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code IntroduceStaticFactoryTool}.
 */
public final class IntroduceStaticFactoryTool {

    static SyncToolSpecification introduceStaticFactory() {
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, MAKE_CONSTRUCTOR_PRIVATE).addRequired(PROJECT_ROOT, FILE, FACTORY_METHOD_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_static_factory", opts.toSchema())
                        .description("Introduce a public static factory method for a constructor and rewrite every new ClassName(...) call site in the project to use it. make_constructor_private (default false): change the constructor visibility to private. Returns a preview of all changed files; does not write to disk.")
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args    = opts.reader(request.arguments());
                        Path root   = args.getPath(PROJECT_ROOT);
                        Path file   = root.resolve(args.getPath(FILE));
                        String name = args.getString(FACTORY_METHOD_NAME);
                        boolean makePrivate = args.getBoolean(MAKE_CONSTRUCTOR_PRIVATE);

                        String source = Files.readString(file);
                        int offset    = LocatorResolver.resolve(args.getLocator(), source, file.getFileName().toString());

                        var changed = JdtIntroduceStaticFactory.introduceStaticFactory(
                                ProjectDetector.detect(root),
                                file, offset, name, makePrivate);

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
