
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
 * Thin MCP tool wrapper for {@code MoveClassTool}. The handler body was produced by
 * the move_static_member refactoring; do not edit by hand.
 */
public final class MoveClassTool {

    static SyncToolSpecification moveClass() {
        Options opts = Options.builder().add(WIDEN_VISIBILITY).addRequired(PROJECT_ROOT, FILE, NEW_PACKAGE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_class", opts.toSchema())
                        .description("""
                        Move a Java class to a new package.
                        Updates the package declaration and all explicit imports in the project.
                        Returns: new class source, new canonical file path, updated import files.
                        Does not write to disk or delete the original file.
                        widen_visibility (default true): if the class is package-private, adds 'public'.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args       = opts.reader(request.arguments());
                        Path file         = args.getPath(FILE);
                        String newPackage = args.getString(NEW_PACKAGE);
                        boolean widen     = args.getBoolean(WIDEN_VISIBILITY, true);
                        var result = JdtMoveClass.moveClass(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                file, newPackage, widen);

                        String imports = result.changedImports().entrySet().stream()
                                .sorted(Map.Entry.comparingByKey())
                                .map(e -> "\n=== " + e.getKey().getFileName() + " (updated import) ===\n"
                                        + e.getValue().stripTrailing() + "\n")
                                .collect(Collectors.joining());
                        return ok(("New path: " + result.newFilePath() + "\n\n"
                                + "=== " + result.newFilePath().getFileName() + " (new) ===\n"
                                + result.newClassSource().stripTrailing() + "\n"
                                + imports).stripTrailing());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
