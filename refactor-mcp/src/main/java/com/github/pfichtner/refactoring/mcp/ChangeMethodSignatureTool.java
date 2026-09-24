
package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.*;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtChangeMethodSignature;
import com.github.pfichtner.refactoring.JdtConvertAnonymousToNested;
import com.github.pfichtner.refactoring.JdtConvertNestedToTopLevel;
import com.github.pfichtner.refactoring.JdtConvertToRecord;
import com.github.pfichtner.refactoring.JdtDecomposeConditional;
import com.github.pfichtner.refactoring.JdtEncapsulateField;
import com.github.pfichtner.refactoring.JdtExtractConstant;
import com.github.pfichtner.refactoring.JdtExtractInterface;
import com.github.pfichtner.refactoring.JdtExtractSuperclass;
import com.github.pfichtner.refactoring.JdtExtractVariable;
import com.github.pfichtner.refactoring.JdtExtractor;
import com.github.pfichtner.refactoring.JdtInlineMethod;
import com.github.pfichtner.refactoring.JdtInliner;
import com.github.pfichtner.refactoring.JdtIntroduceIndirection;
import com.github.pfichtner.refactoring.JdtIntroduceParam;
import com.github.pfichtner.refactoring.JdtIntroduceParameterObject;
import com.github.pfichtner.refactoring.JdtIntroduceStaticFactory;
import com.github.pfichtner.refactoring.JdtMoveClass;
import com.github.pfichtner.refactoring.JdtMoveMethod;
import com.github.pfichtner.refactoring.JdtMoveStaticMember;
import com.github.pfichtner.refactoring.JdtPromoteToField;
import com.github.pfichtner.refactoring.JdtPullUpField;
import com.github.pfichtner.refactoring.JdtPullUpMethod;
import com.github.pfichtner.refactoring.JdtPushDownField;
import com.github.pfichtner.refactoring.JdtPushDownMethod;
import com.github.pfichtner.refactoring.JdtRemoveMethod;
import com.github.pfichtner.refactoring.JdtRemoveParam;
import com.github.pfichtner.refactoring.JdtRenamePackage;
import com.github.pfichtner.refactoring.JdtRenamer;
import com.github.pfichtner.refactoring.SourceUnit;
import com.github.pfichtner.refactoring.locator.LocatorResolver;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ChangeMethodSignatureTool}. The handler body was produced by
 * the move_static_member refactoring; do not edit by hand.
 */
public final class ChangeMethodSignatureTool {

    static SyncToolSpecification changeMethodSignature() {
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS, NEW_RETURN_TYPE, PARAM_ORDER, PARAM_TYPES).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("change_method_signature", opts.toSchema())
                        .description("""
                        Change a method's return type, reorder its parameters, and/or change parameter types — declaration-only edits except param_order which also updates call sites project-wide.
                        Axes (at least one required):
                          new_return_type: new return type text (e.g. "double") — declaration only.
                          param_order: integer array where param_order[i] is the original index of the parameter that should appear at position i — call sites updated.
                          param_types: parallel string array where param_types[i] is the new type for parameter i (null/empty = leave unchanged) — declaration only.
                        Does NOT add or remove parameters; use introduce_param to add and remove_param to drop one.
                        Returns changed file contents; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args  = opts.reader(request.arguments());
                        Path root = args.getPath(PROJECT_ROOT);
                        Path file = root.resolve(args.getPath(FILE));
                        String source = Files.readString(file);
                        int offset = LocatorResolver.resolve(args.getLocator(), source, file.getFileName().toString());
                        String newReturnType = args.getString(NEW_RETURN_TYPE);
                        int[]    paramOrder = args.getIntArray(PARAM_ORDER);
                        String[] paramTypes = args.getStringArray(PARAM_TYPES);
                        var changed = JdtChangeMethodSignature.changeSignature(
                                ProjectDetector.detect(root), file, offset, newReturnType, paramOrder, paramTypes);
                        return ok(formatPreview(changed));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
