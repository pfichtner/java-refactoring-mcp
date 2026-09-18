package dev.mcp.refactor.mcp;

import dev.mcp.refactor.JdtExtractConstant;
import dev.mcp.refactor.JdtExtractInterface;
import dev.mcp.refactor.JdtExtractSuperclass;
import dev.mcp.refactor.JdtIntroduceParam;
import dev.mcp.refactor.JdtRemoveParam;
import dev.mcp.refactor.JdtExtractVariable;
import dev.mcp.refactor.JdtExtractor;
import dev.mcp.refactor.JdtInlineMethod;
import dev.mcp.refactor.JdtInliner;
import dev.mcp.refactor.JdtRenamer;
import dev.mcp.refactor.project.MavenProject;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Builds the MCP server and registers refactoring tools.
 *
 * The tools are thin: they parse MCP arguments, delegate to
 * {@link JdtRenamer} and {@link MavenProject}, and format the result.
 * No refactoring logic lives here.
 */
public class RefactoringServer {

    static final String SERVER_NAME    = "java-refactoring-mcp";
    static final String SERVER_VERSION = "0.1.0";

    /** Builds and returns the configured server (transport already attached). */
    public static McpSyncServer build() {
        var transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .build();

        server.addTool(listRefactorings());
        server.addTool(analyzeRefactoring());
        server.addTool(applyRefactoring());
        server.addTool(extractMethod());
        server.addTool(inlineVariable());
        server.addTool(extractVariable());
        server.addTool(inlineMethod());
        server.addTool(extractConstant());
        server.addTool(introduceParam());
        server.addTool(removeParam());
        server.addTool(extractInterface());
        server.addTool(extractSuperclass());

        return server;
    }

    // -------------------------------------------------------------------------
    // Tool: list_refactorings
    // -------------------------------------------------------------------------

    static SyncToolSpecification listRefactorings() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("list_refactorings",
                        Map.of("type", "object", "properties", Map.of()))
                        .description("List all available refactoring operations.")
                        .build())
                .callHandler((exchange, request) ->
                        ok("""
                        Available refactorings:

                        rename
                          Rename a local variable, parameter, field, method, or type.
                          Required arguments: project_root, file, line, column, new_name
                          Use analyze_refactoring to preview, apply_refactoring to write.

                        extract_method
                          Extract selected statements into a new private method.
                          Required arguments: file, start_line, start_column, end_line, end_column, method_name
                          Returns the rewritten source; does not write to disk.

                        inline_variable
                          Inline a local variable: replace all uses with its initializer, remove the declaration.
                          Required arguments: file, line, column
                          Returns the rewritten source; does not write to disk.

                        extract_variable
                          Extract an expression into a new local variable.
                          Required arguments: file, start_line, start_column, end_line, end_column, var_name
                          Optional: replace_all (replace all identical occurrences in the block)
                          Returns the rewritten source; does not write to disk.
                        """))
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: analyze_refactoring  (preview / dry-run)
    // -------------------------------------------------------------------------

    static SyncToolSpecification analyzeRefactoring() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("analyze_refactoring", renameSchema())
                        .description("""
                        Preview what a refactoring would change without modifying files on disk.
                        Returns the new source of every file that would be affected.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var changed = executeRename(request.arguments());
                        return ok(formatPreview(changed));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: apply_refactoring
    // -------------------------------------------------------------------------

    static SyncToolSpecification applyRefactoring() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("apply_refactoring", renameSchema())
                        .description("""
                        Apply a refactoring and write the changes to disk.
                        Returns a summary of which files were modified.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var changed = executeRename(request.arguments());
                        for (var entry : changed.entrySet()) {
                            Files.writeString(entry.getKey(), entry.getValue());
                        }
                        return ok(formatSummary(changed));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: extract_method
    // -------------------------------------------------------------------------

    static SyncToolSpecification extractMethod() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_method", extractSchema())
                        .description("""
                        Extract selected statements into a new private method.
                        The selection is specified as start/end line+column (1-based).
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file       = (String) args.get("file");
                        int startLine     = ((Number) args.get("start_line")).intValue();
                        int startCol      = ((Number) args.get("start_column")).intValue();
                        int endLine       = ((Number) args.get("end_line")).intValue();
                        int endCol        = ((Number) args.get("end_column")).intValue();
                        String methodName = (String) args.get("method_name");

                        String source  = Files.readString(Path.of(file));
                        int selStart   = JdtRenamer.toOffset(source, startLine, startCol);
                        int selEnd     = JdtRenamer.toOffset(source, endLine, endCol);
                        String result  = JdtExtractor.extractMethod(
                                source, Path.of(file).getFileName().toString(),
                                selStart, selEnd - selStart, methodName);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    private static Map<String, Object> extractSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "file",         Map.of("type", "string",  "description", "Absolute path to the source file."),
                        "start_line",   Map.of("type", "integer", "description", "1-based start line of the selection."),
                        "start_column", Map.of("type", "integer", "description", "1-based start column of the selection."),
                        "end_line",     Map.of("type", "integer", "description", "1-based end line of the selection."),
                        "end_column",   Map.of("type", "integer", "description", "1-based end column of the selection (exclusive)."),
                        "method_name",  Map.of("type", "string",  "description", "Name for the extracted method.")
                ),
                "required", List.of("file", "start_line", "start_column",
                        "end_line", "end_column", "method_name")
        );
    }

    // -------------------------------------------------------------------------
    // Tool: extract_superclass
    // -------------------------------------------------------------------------

    static SyncToolSpecification extractSuperclass() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_superclass", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file",            Map.of("type", "string", "description", "Absolute path to the class file."),
                                "superclass_name", Map.of("type", "string", "description", "Simple name for the new abstract superclass."),
                                "method_names",    Map.of("type", "array",  "items", Map.of("type", "string"),
                                                          "description", "Methods to move; empty = all public non-static.")
                        ),
                        "required", List.of("file", "superclass_name")))
                        .description("""
                        Move public methods into a new abstract superclass and make the class extend it.
                        Returns the modified class source and the new superclass source.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file          = (String) args.get("file");
                        String superName     = (String) args.get("superclass_name");
                        @SuppressWarnings("unchecked")
                        List<String> methods = args.containsKey("method_names")
                                ? (List<String>) args.get("method_names") : List.of();
                        String source = Files.readString(Path.of(file));
                        var result = JdtExtractSuperclass.extractSuperclass(
                                source, Path.of(file).getFileName().toString(), superName, methods);
                        String out = "=== " + Path.of(file).getFileName() + " (modified) ===\n"
                                + result.modifiedClassSource().stripTrailing() + "\n\n"
                                + "=== " + superName + ".java (new) ===\n"
                                + result.superclassSource().stripTrailing();
                        return ok(out);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: extract_interface
    // -------------------------------------------------------------------------

    static SyncToolSpecification extractInterface() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_interface", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file",           Map.of("type", "string",  "description", "Absolute path to the class file."),
                                "interface_name", Map.of("type", "string",  "description", "Simple name for the new interface."),
                                "method_names",   Map.of("type", "array",   "items", Map.of("type", "string"),
                                                         "description", "Methods to include; empty = all public non-static.")
                        ),
                        "required", List.of("file", "interface_name")))
                        .description("""
                        Extract a new interface from the public methods of a class.
                        Returns the modified class source and the new interface source.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file          = (String) args.get("file");
                        String interfaceName = (String) args.get("interface_name");
                        @SuppressWarnings("unchecked")
                        List<String> methods = args.containsKey("method_names")
                                ? (List<String>) args.get("method_names") : List.of();

                        String source = Files.readString(Path.of(file));
                        var result = JdtExtractInterface.extractInterface(
                                source, Path.of(file).getFileName().toString(),
                                interfaceName, methods);

                        String out = "=== " + Path.of(file).getFileName() + " (modified) ===\n"
                                + result.modifiedClassSource().stripTrailing() + "\n\n"
                                + "=== " + interfaceName + ".java (new) ===\n"
                                + result.interfaceSource().stripTrailing();
                        return ok(out);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: remove_param
    // -------------------------------------------------------------------------

    static SyncToolSpecification removeParam() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("remove_param", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "project_root", Map.of("type", "string",  "description", "Absolute Maven project root."),
                                "file",         Map.of("type", "string",  "description", "Absolute path to the file with the parameter."),
                                "line",         Map.of("type", "integer", "description", "1-based line of the parameter declaration."),
                                "column",       Map.of("type", "integer", "description", "1-based column of the parameter name.")
                        ),
                        "required", List.of("project_root", "file", "line", "column")))
                        .description("""
                        Remove an unused parameter from a method and the corresponding argument
                        from every call site in the project.
                        Returns a map of filename → new source for each changed file.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file = (String) args.get("file");
                        int line    = ((Number) args.get("line")).intValue();
                        int col     = ((Number) args.get("column")).intValue();
                        String source = Files.readString(Path.of(file));
                        int offset    = JdtRenamer.toOffset(source, line, col);
                        var changed = JdtRemoveParam.removeParam(
                                new dev.mcp.refactor.project.MavenProject(
                                        Path.of((String) args.get("project_root"))),
                                Path.of(file), offset);
                        StringBuilder sb = new StringBuilder();
                        changed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                                .forEach(e -> sb.append("=== ").append(e.getKey().getFileName())
                                        .append(" ===\n").append(e.getValue().stripTrailing()).append("\n\n"));
                        return ok(sb.toString().stripTrailing());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: introduce_param
    // -------------------------------------------------------------------------

    static SyncToolSpecification introduceParam() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_param", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "project_root",  Map.of("type", "string",  "description", "Absolute Maven project root."),
                                "file",          Map.of("type", "string",  "description", "Absolute path to the source file."),
                                "start_line",    Map.of("type", "integer", "description", "1-based start line of the expression."),
                                "start_column",  Map.of("type", "integer", "description", "1-based start column."),
                                "end_line",      Map.of("type", "integer", "description", "1-based end line."),
                                "end_column",    Map.of("type", "integer", "description", "1-based end column (exclusive)."),
                                "param_name",    Map.of("type", "string",  "description", "Name for the new parameter."),
                                "param_type",    Map.of("type", "string",  "description", "Explicit type (inferred if omitted).")
                        ),
                        "required", List.of("project_root", "file", "start_line", "start_column",
                                "end_line", "end_column", "param_name")))
                        .description("""
                        Promote an expression to a method parameter.
                        Updates the method signature and all call sites in the project.
                        Returns a map of filename → new source for each changed file.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file       = (String) args.get("file");
                        int startLine     = ((Number) args.get("start_line")).intValue();
                        int startCol      = ((Number) args.get("start_column")).intValue();
                        int endLine       = ((Number) args.get("end_line")).intValue();
                        int endCol        = ((Number) args.get("end_column")).intValue();
                        String paramName  = (String) args.get("param_name");
                        String paramType  = (String) args.get("param_type"); // nullable

                        String source  = Files.readString(Path.of(file));
                        int selStart   = JdtRenamer.toOffset(source, startLine, startCol);
                        int selEnd     = JdtRenamer.toOffset(source, endLine, endCol);

                        var changed = JdtIntroduceParam.introduceParam(
                                new dev.mcp.refactor.project.MavenProject(Path.of((String) args.get("project_root"))),
                                Path.of(file), selStart, selEnd - selStart, paramName, paramType);

                        StringBuilder sb = new StringBuilder();
                        changed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                                .forEach(e -> sb.append("=== ").append(e.getKey().getFileName())
                                        .append(" ===\n").append(e.getValue().stripTrailing()).append("\n\n"));
                        return ok(sb.toString().stripTrailing());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: extract_constant
    // -------------------------------------------------------------------------

    static SyncToolSpecification extractConstant() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_constant", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file",         Map.of("type", "string",  "description", "Absolute path to the source file."),
                                "start_line",   Map.of("type", "integer", "description", "1-based start line of the expression."),
                                "start_column", Map.of("type", "integer", "description", "1-based start column."),
                                "end_line",     Map.of("type", "integer", "description", "1-based end line."),
                                "end_column",   Map.of("type", "integer", "description", "1-based end column (exclusive)."),
                                "const_name",   Map.of("type", "string",  "description", "Name for the constant (conventionally UPPER_CASE)."),
                                "replace_all",  Map.of("type", "boolean", "description", "Replace all identical occurrences in the class.")
                        ),
                        "required", List.of("file", "start_line", "start_column",
                                "end_line", "end_column", "const_name")))
                        .description("""
                        Extract an expression into a private static final constant at class level.
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file       = (String) args.get("file");
                        int startLine     = ((Number) args.get("start_line")).intValue();
                        int startCol      = ((Number) args.get("start_column")).intValue();
                        int endLine       = ((Number) args.get("end_line")).intValue();
                        int endCol        = ((Number) args.get("end_column")).intValue();
                        String constName  = (String) args.get("const_name");
                        boolean replaceAll = Boolean.TRUE.equals(args.get("replace_all"));

                        String source = Files.readString(Path.of(file));
                        int selStart  = JdtRenamer.toOffset(source, startLine, startCol);
                        int selEnd    = JdtRenamer.toOffset(source, endLine, endCol);
                        return ok(JdtExtractConstant.extractConstant(
                                source, Path.of(file).getFileName().toString(),
                                selStart, selEnd - selStart, constName, replaceAll));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: inline_method
    // -------------------------------------------------------------------------

    static SyncToolSpecification inlineMethod() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_method", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file",   Map.of("type", "string",  "description", "Absolute path to the source file."),
                                "line",   Map.of("type", "integer", "description", "1-based line of the method call."),
                                "column", Map.of("type", "integer", "description", "1-based column of the method call name.")
                        ),
                        "required", List.of("file", "line", "column")))
                        .description("""
                        Inline a method call: replace it with the method body (single file only).
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file = (String) args.get("file");
                        int line    = ((Number) args.get("line")).intValue();
                        int col     = ((Number) args.get("column")).intValue();
                        String source = Files.readString(Path.of(file));
                        int offset    = JdtRenamer.toOffset(source, line, col);
                        return ok(JdtInlineMethod.inlineMethod(
                                source, Path.of(file).getFileName().toString(), offset));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: extract_variable
    // -------------------------------------------------------------------------

    static SyncToolSpecification extractVariable() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_variable", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file",         Map.of("type", "string",  "description", "Absolute path to the source file."),
                                "start_line",   Map.of("type", "integer", "description", "1-based start line of the expression."),
                                "start_column", Map.of("type", "integer", "description", "1-based start column."),
                                "end_line",     Map.of("type", "integer", "description", "1-based end line of the expression."),
                                "end_column",   Map.of("type", "integer", "description", "1-based end column (exclusive)."),
                                "var_name",     Map.of("type", "string",  "description", "Name for the introduced variable."),
                                "replace_all",  Map.of("type", "boolean", "description", "Replace all identical occurrences in the block.")
                        ),
                        "required", List.of("file", "start_line", "start_column",
                                "end_line", "end_column", "var_name")))
                        .description("""
                        Extract an expression into a new local variable.
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file       = (String) args.get("file");
                        int startLine     = ((Number) args.get("start_line")).intValue();
                        int startCol      = ((Number) args.get("start_column")).intValue();
                        int endLine       = ((Number) args.get("end_line")).intValue();
                        int endCol        = ((Number) args.get("end_column")).intValue();
                        String varName    = (String) args.get("var_name");
                        boolean replaceAll = Boolean.TRUE.equals(args.get("replace_all"));

                        String source  = Files.readString(Path.of(file));
                        int selStart   = JdtRenamer.toOffset(source, startLine, startCol);
                        int selEnd     = JdtRenamer.toOffset(source, endLine, endCol);
                        String result  = JdtExtractVariable.extractVariable(
                                source, Path.of(file).getFileName().toString(),
                                selStart, selEnd - selStart, varName, replaceAll);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: inline_variable
    // -------------------------------------------------------------------------

    static SyncToolSpecification inlineVariable() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_variable", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file",   Map.of("type", "string",  "description", "Absolute path to the source file."),
                                "line",   Map.of("type", "integer", "description", "1-based line number of the variable."),
                                "column", Map.of("type", "integer", "description", "1-based column number of the variable name.")
                        ),
                        "required", List.of("file", "line", "column")))
                        .description("""
                        Inline a local variable: replace every use with its initializer expression
                        and remove the declaration. Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file = (String) args.get("file");
                        int line    = ((Number) args.get("line")).intValue();
                        int col     = ((Number) args.get("column")).intValue();

                        String source = Files.readString(Path.of(file));
                        int offset    = JdtRenamer.toOffset(source, line, col);
                        String result = JdtInliner.inlineVariable(
                                source, Path.of(file).getFileName().toString(), offset);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Shared engine call
    // -------------------------------------------------------------------------

    static Map<Path, String> executeRename(Map<String, Object> args)
            throws Exception {
        String projectRoot = (String) args.get("project_root");
        String file        = (String) args.get("file");
        int line           = ((Number) args.get("line")).intValue();
        int col            = ((Number) args.get("column")).intValue();
        String newName     = (String) args.get("new_name");
        String refactoring = (String) args.getOrDefault("refactoring", "rename");

        if (!"rename".equals(refactoring)) {
            throw new IllegalArgumentException(
                    "Unsupported refactoring type: '" + refactoring
                    + "'. Supported: rename");
        }

        Path root       = Path.of(projectRoot);
        Path sourceFile = Path.of(file).isAbsolute()
                ? Path.of(file)
                : root.resolve(file);

        String source = Files.readString(sourceFile);
        int offset    = JdtRenamer.toOffset(source, line, col);

        return JdtRenamer.rename(new MavenProject(root), sourceFile, offset, newName);
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    static String formatPreview(Map<Path, String> changed) {
        if (changed.isEmpty()) return "No changes.";
        var sb = new StringBuilder();
        sb.append("Dry run — no files written.\n");
        String names = changed.keySet().stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .reduce((a, b) -> a + ", " + b).orElse("");
        sb.append("Changed files (").append(changed.size()).append("): ").append(names).append("\n");
        changed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    sb.append("\n=== ").append(e.getKey().getFileName()).append(" ===\n");
                    sb.append(e.getValue().stripTrailing()).append("\n");
                });
        return sb.toString();
    }

    static String formatSummary(Map<Path, String> changed) {
        if (changed.isEmpty()) return "No changes.";
        var sb = new StringBuilder();
        sb.append("Renamed in ").append(changed.size()).append(" file(s):\n");
        changed.keySet().stream().sorted()
                .forEach(p -> sb.append("  ").append(p.getFileName()).append("\n"));
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Schema + result helpers
    // -------------------------------------------------------------------------

    private static Map<String, Object> renameSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "project_root", Map.of("type", "string",
                                "description", "Absolute path to the Maven project root."),
                        "file", Map.of("type", "string",
                                "description", "Source file — absolute or relative to project_root."),
                        "line", Map.of("type", "integer",
                                "description", "1-based line number of the symbol to rename."),
                        "column", Map.of("type", "integer",
                                "description", "1-based column number of the symbol to rename."),
                        "refactoring", Map.of("type", "string",
                                "description", "Refactoring type. Currently supported: \"rename\"."),
                        "new_name", Map.of("type", "string",
                                "description", "New name for the symbol.")
                ),
                "required", List.of("project_root", "file", "line", "column",
                        "refactoring", "new_name")
        );
    }

    private static CallToolResult ok(String text) {
        return CallToolResult.builder()
                .content(List.of(new TextContent(text)))
                .isError(false)
                .build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(new TextContent("Error: " + message)))
                .isError(true)
                .build();
    }
}
