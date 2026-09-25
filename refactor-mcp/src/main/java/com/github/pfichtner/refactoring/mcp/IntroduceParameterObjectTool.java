package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.DRY_RUN;
import static com.github.pfichtner.refactoring.mcp.Property.AS_RECORD;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_NAMES;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_OBJECT_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.changedMap;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.commit;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.execute;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.formatDryrun;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;

import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.JdtIntroduceParameterObject;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code IntroduceParameterObjectTool}.
 */
public final class IntroduceParameterObjectTool {

    static SyncToolSpecification introduceParameterObject() {
        Options opts = Options.builder().required(PROJECT_ROOT, FILE, PARAM_NAMES, CLASS_NAME).optional(DRY_RUN, LINE, COLUMN, METHOD, CLASS, PARAM_OBJECT_NAME, AS_RECORD).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_parameter_object", opts.toSchema())
                        .description("Group selected method parameters into a new class (record if as_record=true). Updates the method signature and every call site in the project. Returns new source for each changed file. Applies by default; pass dryrun=true to preview instead.")
                        .build())
                .callHandler((exchange, request) -> execute(() -> {
                    var args  = opts.reader(request.arguments());
                    Path root = args.getPath(PROJECT_ROOT);
                    SourceFile source = args.getContent(FILE, root);
                    List<String> paramNames = args.getStringList(PARAM_NAMES);
                    String className = args.getString(CLASS_NAME);
                    String paramObjName = args.has(PARAM_OBJECT_NAME)
                            ? args.getString(PARAM_OBJECT_NAME)
                            : Character.toLowerCase(className.charAt(0)) + className.substring(1);

                    int offset    = source.resolve(args.getLocator());

                    boolean asRecord = args.getBoolean(AS_RECORD);
                    var changed = JdtIntroduceParameterObject.introduce(
                            ProjectDetector.detect(root),
                            source.path(), offset, paramNames, className, paramObjName, asRecord);
                    return args.getBoolean(DRY_RUN)
                            ? ok(formatDryrun(changed))
                            : ok(commit(changedMap(changed)));
                }))
                .build();
    }
}
