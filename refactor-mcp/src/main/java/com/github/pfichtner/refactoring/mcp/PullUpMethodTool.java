
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
 * Thin MCP tool wrapper for {@code PullUpMethodTool}. The handler body was produced by
 * the move_static_member refactoring; do not edit by hand.
 */
public final class PullUpMethodTool {

    static SyncToolSpecification pullUpMethod() {
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS, WIDEN_VISIBILITY).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("pull_up_method", opts.toSchema())
                        .description("""
                        Pull a method up from a subclass to its direct superclass.
                        The superclass must be in the project source roots.
                        Returns new source for both the subclass and the superclass.
                        Does not write to disk.
                        widen_visibility (default true): if the method is private, changes it to protected in the superclass.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        Path file   = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset    = LocatorResolver.resolve(args.getLocator(), source, file.getFileName().toString());
                        boolean widen = args.getBoolean(WIDEN_VISIBILITY, true);
                        var changed   = JdtPullUpMethod.pullUp(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                file, offset, widen);
                        return ok(changed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                                .map(e -> "=== " + e.getKey().getFileName() + " ===\n"
                                        + e.getValue().stripTrailing() + "\n\n")
                                .collect(Collectors.joining()).stripTrailing());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
