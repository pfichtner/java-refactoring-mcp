package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.RefactoringServer.TOOLS;
import static com.github.pfichtner.refactoring.mcp.ToolSupport.ok;
import static java.util.function.Predicate.not;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * Thin MCP tool wrapper for {@code ListRefactoringsTool}.
 */
public final class ListRefactoringsTool {

    /** Workflow tools that are not themselves refactorings; omitted from the listing. */
    static final Set<String> META_TOOLS = Set.of(
            "list_refactorings", "analyze_refactoring", "apply_refactoring");

    static SyncToolSpecification listRefactorings() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("list_refactorings",
                        Map.of("type", "object", "properties", Map.of()))
                        .description("List all available refactoring operations.")
                        .build())
                .callHandler((exchange, request) -> ok(describeRefactorings()))
                .build();
    }

    /**
     * Renders the listing from the shared {@link #TOOLS} registry, so it always
     * matches what the server actually exposes.
     */
    static String describeRefactorings() {
        List<SyncToolSpecification> refactorings = TOOLS.stream()
                .filter(not(ListRefactoringsTool::isMetaTool))
                .toList();

        return Stream.concat(
                        Stream.of("Available refactorings (" + refactorings.size() + "):\n"),
                        refactorings.stream().map(ListRefactoringsTool::describeTool))
                .collect(Collectors.joining())
                .stripTrailing();
    }

	private static boolean isMetaTool(SyncToolSpecification spec) {
		return META_TOOLS.contains(spec.tool().name());
	}

	private static String describeTool(SyncToolSpecification spec) {
		return describeTool(spec.tool());
	}
	
    private static String describeTool(Tool tool) {
        String description = Arrays.stream(tool.description().split("\n"))
                .map(String::stripTrailing)
                .filter(not(String::isBlank))
                .map(line -> "  " + line + "\n")
                .collect(Collectors.joining());

		return Stream.of(tool.name(), description + "  Required: " + requiredArgs(tool))
				.collect(Collectors.joining("\n", "\n", "\n"));
    }

    static String requiredArgs(Tool tool) {
        Object required = tool.inputSchema().get("required");
        return required instanceof List<?> list ? list.stream().map(String::valueOf).collect(Collectors.joining(", ")) : "";
    }

}
