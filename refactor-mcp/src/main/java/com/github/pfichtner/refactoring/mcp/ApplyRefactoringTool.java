
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
 * Thin MCP tool wrapper for {@code ApplyRefactoringTool}. The handler body was produced by
 * the move_static_member refactoring; do not edit by hand.
 */
public final class ApplyRefactoringTool {

    static SyncToolSpecification applyRefactoring() {
        Options opts = renameOptions();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("apply_refactoring", opts.toSchema())
                        .description("""
                        Apply a refactoring and write the changes to disk.
                        Returns a summary of which files were modified.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var changed = executeRename(opts.reader(request.arguments()));
                        for (FileChange fc : changed) {
                            Files.createDirectories(fc.newPath().getParent());
                            Files.writeString(fc.newPath(), fc.newSource());
                            if (fc.pathChanged()) Files.deleteIfExists(fc.oldPath());
                        }
                        return ok(formatSummary(changed));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }
}
