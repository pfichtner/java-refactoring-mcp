package com.github.pfichtner.refactoring.mcp;

import java.util.List;
import java.util.Set;

import com.github.pfichtner.refactoring.JdtRenamer;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

/**
 * Builds the MCP server and registers refactoring tools.
 *
 * The tools are thin: they parse MCP arguments, delegate to
 * {@link JdtRenamer} and {@link ProjectDetector}, and format the result.
 * No refactoring logic lives here.
 */
public class RefactoringServer {

    static final String SERVER_NAME    = "java-refactoring-mcp";
    static final String SERVER_VERSION = readVersion();

    private static String readVersion() {
        String version = RefactoringServer.class.getPackage().getImplementationVersion();
        return version != null ? version : "unknown";
    }

    /**
     * Single registry of every tool spec. Used both to build the server and to
     * render {@code list_refactorings}, so the two can never drift apart.
     */
    static final List<SyncToolSpecification> TOOLS = List.of(
            ListRefactoringsTool.listRefactorings(),
            AnalyzeRefactoringTool.analyzeRefactoring(),
            ApplyRefactoringTool.applyRefactoring(),
            ExtractMethodTool.extractMethod(),
            InlineVariableTool.inlineVariable(),
            InlineConstantTool.inlineConstant(),
            ExtractVariableTool.extractVariable(),
            InlineMethodTool.inlineMethod(),
            ExtractConstantTool.extractConstant(),
            IntroduceParamTool.introduceParam(),
            RemoveParamTool.removeParam(),
            RemoveMethodTool.removeMethod(),
            ExtractInterfaceTool.extractInterface(),
            ExtractSuperclassTool.extractSuperclass(),
            MoveClassTool.moveClass(),
            RenamePackageTool.renamePackage(),
            PullUpMethodTool.pullUpMethod(),
            PushDownMethodTool.pushDownMethod(),
            MoveMethodTool.moveMethod(),
            PullUpFieldTool.pullUpField(),
            PushDownFieldTool.pushDownField(),
            IntroduceStaticFactoryTool.introduceStaticFactory(),
            IntroduceParameterObjectTool.introduceParameterObject(),
            ConvertToRecordTool.convertToRecord(),
            ChangeMethodSignatureTool.changeMethodSignature(),
            EncapsulateFieldTool.encapsulateField(),
            DecomposeConditionalTool.decomposeConditional(),
            ConvertAnonymousToNestedTool.convertAnonymousToNested(),
            ConvertNestedToTopLevelTool.convertNestedToTopLevel(),
            PromoteToFieldTool.promoteToField(),
            MoveStaticMemberTool.moveStaticMember(),
            IntroduceIndirectionTool.introduceIndirection()
    );

    /** Workflow tools that are not themselves refactorings; omitted from the listing. */
    static final Set<String> META_TOOLS = Set.of(
            "list_refactorings", "analyze_refactoring", "apply_refactoring");

    /** Builds and returns the configured server (transport already attached). */
    public static McpSyncServer build() {
        var transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .tools(TOOLS)
                .build();
    }
}