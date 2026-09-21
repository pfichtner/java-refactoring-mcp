package com.github.pfichtner.mcp;

import com.github.pfichtner.JdtConvertAnonymousToNested;
import com.github.pfichtner.JdtConvertNestedToTopLevel;
import com.github.pfichtner.JdtIntroduceIndirection;
import com.github.pfichtner.JdtMoveStaticMember;
import com.github.pfichtner.JdtPromoteToField;
import com.github.pfichtner.JdtExtractConstant;
import com.github.pfichtner.JdtMoveClass;
import com.github.pfichtner.JdtRenamePackage;
import com.github.pfichtner.JdtExtractInterface;
import com.github.pfichtner.JdtExtractSuperclass;
import com.github.pfichtner.JdtIntroduceParam;
import com.github.pfichtner.JdtRemoveMethod;
import com.github.pfichtner.JdtRemoveParam;
import com.github.pfichtner.JdtExtractVariable;
import com.github.pfichtner.JdtExtractor;
import com.github.pfichtner.JdtInlineMethod;
import com.github.pfichtner.JdtInliner;
import com.github.pfichtner.JdtConvertToRecord;
import com.github.pfichtner.JdtIntroduceParameterObject;
import com.github.pfichtner.JdtIntroduceStaticFactory;
import com.github.pfichtner.JdtChangeMethodSignature;
import com.github.pfichtner.JdtDecomposeConditional;
import com.github.pfichtner.JdtEncapsulateField;
import com.github.pfichtner.JdtPullUpField;
import com.github.pfichtner.JdtMoveMethod;
import com.github.pfichtner.JdtPullUpMethod;
import com.github.pfichtner.JdtPushDownField;
import com.github.pfichtner.JdtPushDownMethod;
import com.github.pfichtner.FileChange;
import com.github.pfichtner.JdtRenamer;
import com.github.pfichtner.locator.Locator;
import com.github.pfichtner.locator.LocatorResolver;
import com.github.pfichtner.project.ProjectDetector;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the MCP server and registers refactoring tools.
 *
 * The tools are thin: they parse MCP arguments, delegate to
 * {@link JdtRenamer} and {@link ProjectDetector}, and format the result.
 * No refactoring logic lives here.
 */
public class RefactoringServer {

    static final String SERVER_NAME    = "java-refactoring-mcp";
    static final String SERVER_VERSION = "0.1.0";

    /** Builds and returns the configured server (transport already attached). */
    public static McpSyncServer build() {
        var transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .tools(
                        listRefactorings(),
                        analyzeRefactoring(),
                        applyRefactoring(),
                        extractMethod(),
                        inlineVariable(),
                        inlineConstant(),
                        extractVariable(),
                        inlineMethod(),
                        extractConstant(),
                        introduceParam(),
                        removeParam(),
                        removeMethod(),
                        extractInterface(),
                        extractSuperclass(),
                        moveClass(),
                        renamePackage(),
                        pullUpMethod(),
                        pushDownMethod(),
                        moveMethod(),
                        pullUpField(),
                        pushDownField(),
                        introduceStaticFactory(),
                        introduceParameterObject(),
                        convertToRecord(),
                        changeMethodSignature(),
                        encapsulateField(),
                        decomposeConditional(),
                        convertAnonymousToNested(),
                        convertNestedToTopLevel(),
                        promoteToField(),
                        moveStaticMember(),
                        introduceIndirection()
                )
                .build();
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

                        introduce_static_factory
                          Introduce a public static factory method for a constructor and rewrite
                          every new ClassName(...) call site in the project to use it.
                          Required arguments: project_root, file, line, column, factory_method_name
                          Optional: make_constructor_private (default false)
                          Returns a preview of all changed files.
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
    // Tool: rename_package
    // -------------------------------------------------------------------------

    static SyncToolSpecification renamePackage() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("rename_package", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "project_root", Map.of("type", "string", "description", "Absolute project root (Maven or Gradle)."),
                                "old_package",  Map.of("type", "string", "description", "Fully-qualified old package, e.g. com.example.service."),
                                "new_package",  Map.of("type", "string", "description", "Fully-qualified new package, e.g. com.example.util.")
                        ),
                        "required", List.of("project_root", "old_package", "new_package")))
                        .description("""
                        Rename a package across the project.
                        Updates package declarations, single-class imports, and wildcard imports.
                        Returns a list of changed files with their new sources and new paths.
                        Does not write to disk or delete original files.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        var result = JdtRenamePackage.renamePackage(
                                ProjectDetector.detect(
                                        Path.of((String) args.get("project_root"))),
                                (String) args.get("old_package"),
                                (String) args.get("new_package"));
                        return ok(result.changedFiles().stream()
                                .map(fc -> "=== " + fc.newPath().getFileName()
                                        + (fc.pathChanged() ? " (moved from " + fc.oldPath().getFileName() + ")" : "")
                                        + " ===\n" + fc.newSource().stripTrailing() + "\n\n")
                                .collect(Collectors.joining()).stripTrailing());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: move_class
    // -------------------------------------------------------------------------

    static SyncToolSpecification moveClass() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_class", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "project_root", Map.of("type", "string", "description", "Absolute project root (Maven or Gradle)."),
                                "file",         Map.of("type", "string", "description", "Absolute path to the .java file to move."),
                                "new_package",  Map.of("type", "string", "description", "Target package, e.g. com.example.util.")
                        ),
                        "required", List.of("project_root", "file", "new_package")))
                        .description("""
                        Move a Java class to a new package.
                        Updates the package declaration and all explicit imports in the project.
                        Returns: new class source, new canonical file path, updated import files.
                        Does not write to disk or delete the original file.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file       = (String) args.get("file");
                        String newPackage = (String) args.get("new_package");
                        var result = JdtMoveClass.moveClass(
                                ProjectDetector.detect(
                                        Path.of((String) args.get("project_root"))),
                                Path.of(file), newPackage);

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
                        "properties", Map.ofEntries(
                                Map.entry("project_root", Map.of("type", "string",  "description", "Absolute project root (Maven or Gradle).")),
                                Map.entry("file",         Map.of("type", "string",  "description", "Absolute path to the file with the parameter.")),
                                Map.entry("line",         Map.of("type", "integer", "description", "1-based line of the parameter. Use with 'column' OR use method+parameter name-based locators.")),
                                Map.entry("column",       Map.of("type", "integer", "description", "1-based column of the parameter name. Use with 'line'.")),
                                Map.entry("method",       Map.of("type", "string",  "description", "Name-based: method containing the parameter, e.g. \"setName\" or \"setName(String)\".")),
                                Map.entry("parameter",    Map.of("type", "string",  "description", "Name-based: identifier of the parameter to remove, e.g. \"unused\". Requires 'method'.")),
                                Map.entry("class",        Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types."))
                        ),
                        "required", List.of("project_root", "file")))
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
                        String source = Files.readString(Path.of(file));
                        int offset    = resolveOffset(args, source, Path.of(file).getFileName().toString());
                        var changed = JdtRemoveParam.removeParam(
                                ProjectDetector.detect(
                                        Path.of((String) args.get("project_root"))),
                                Path.of(file), offset);
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

    // -------------------------------------------------------------------------
    // Tool: remove_method
    // -------------------------------------------------------------------------

    static SyncToolSpecification removeMethod() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("remove_method", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("project_root", Map.of("type", "string",  "description", "Absolute project root (Maven or Gradle).")),
                                Map.entry("file",         Map.of("type", "string",  "description", "Absolute path to the file containing the method.")),
                                Map.entry("line",         Map.of("type", "integer", "description", "1-based line of the method name. Use with 'column' OR use the 'method' name-based locator.")),
                                Map.entry("column",       Map.of("type", "integer", "description", "1-based column of the method name. Use with 'line'.")),
                                Map.entry("method",       Map.of("type", "string",  "description", "Name-based locator: method name, e.g. \"print\" or \"print()\". Use instead of line/column.")),
                                Map.entry("class",        Map.of("type", "string",  "description", "Optional: restrict name-based search to this class when the file has multiple types.")),
                                Map.entry("cascade",      Map.of("type", "boolean", "description", "If true (default), also remove overriding methods in subclasses and implementing methods in implementing classes."))
                        ),
                        "required", List.of("project_root", "file")))
                        .description("""
                        Remove a method from a class or interface.
                        When cascade is true (the default), also removes every overriding method
                        in subclasses and every implementing method in implementing classes.
                        Returns a map of filename → new source for each changed file.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file   = (String) args.get("file");
                        String source = Files.readString(Path.of(file));
                        int offset    = resolveOffset(args, source, Path.of(file).getFileName().toString());
                        boolean cascade = args.get("cascade") == null || Boolean.TRUE.equals(args.get("cascade"));
                        var changed = JdtRemoveMethod.removeMethod(
                                ProjectDetector.detect(Path.of((String) args.get("project_root"))),
                                Path.of(file), offset, cascade);
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

    // -------------------------------------------------------------------------
    // Tool: introduce_param
    // -------------------------------------------------------------------------

    static SyncToolSpecification introduceParam() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_param", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "project_root",  Map.of("type", "string",  "description", "Absolute project root (Maven or Gradle)."),
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
                                ProjectDetector.detect(Path.of((String) args.get("project_root"))),
                                Path.of(file), selStart, selEnd - selStart, paramName, paramType);

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
                        "properties", Map.ofEntries(
                                Map.entry("project_root",       Map.of("type", "string",  "description", "Absolute project root (Maven or Gradle; enables multi-file inline).")),
                                Map.entry("file",               Map.of("type", "string",  "description", "Absolute path to the file containing the call.")),
                                Map.entry("line",               Map.of("type", "integer", "description", "1-based line of the method call. Use with 'column' OR use 'method' name-based locator.")),
                                Map.entry("column",             Map.of("type", "integer", "description", "1-based column of the method call name. Use with 'line'.")),
                                Map.entry("method",             Map.of("type", "string",  "description", "Name-based locator: method name, e.g. \"helper\" or \"helper(int)\".")),
                                Map.entry("class",              Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types.")),
                                Map.entry("remove_declaration", Map.of("type", "boolean", "description", "Also remove the method declaration (default false)."))
                        ),
                        "required", List.of("project_root", "file")))
                        .description("""
                        Inline a method call at all call sites in the project.
                        Optionally removes the method declaration.
                        Returns new source for every changed file; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file = (String) args.get("file");
                        boolean removeDel = Boolean.TRUE.equals(args.get("remove_declaration"));
                        String source = Files.readString(Path.of(file));
                        int offset    = resolveOffset(args, source, Path.of(file).getFileName().toString());
                        var changed = JdtInlineMethod.inlineMethod(
                                ProjectDetector.detect(
                                        Path.of((String) args.get("project_root"))),
                                Path.of(file), offset, removeDel);
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
                                "file",     Map.of("type", "string",  "description", "Absolute path to the source file."),
                                "line",     Map.of("type", "integer", "description", "1-based line number. Use with 'column' OR use 'variable' name-based locator."),
                                "column",   Map.of("type", "integer", "description", "1-based column number. Use with 'line'."),
                                "variable", Map.of("type", "string",  "description", "Name-based locator: local variable name, e.g. \"result\".")
                        ),
                        "required", List.of("file")))
                        .description("""
                        Inline a local variable: replace every use with its initializer expression
                        and remove the declaration. Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file = (String) args.get("file");

                        String source = Files.readString(Path.of(file));
                        int offset    = resolveOffset(args, source, Path.of(file).getFileName().toString());
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
    // Tool: inline_constant
    // -------------------------------------------------------------------------

    static SyncToolSpecification inlineConstant() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_constant", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("file",               Map.of("type", "string",  "description", "Absolute path to the source file.")),
                                Map.entry("line",               Map.of("type", "integer", "description", "1-based line of the constant. Use with 'column' OR use 'field' name-based locator.")),
                                Map.entry("column",             Map.of("type", "integer", "description", "1-based column of the constant name. Use with 'line'.")),
                                Map.entry("field",              Map.of("type", "string",  "description", "Name-based locator: constant field name, e.g. \"MAX_SIZE\".")),
                                Map.entry("class",              Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types.")),
                                Map.entry("all_occurrences",    Map.of("type", "boolean", "description", "Replace all references in the file (default: only the reference at line/column).")),
                                Map.entry("remove_declaration", Map.of("type", "boolean", "description", "Also delete the field declaration (requires all_occurrences=true)."))
                        ),
                        "required", List.of("file")))
                        .description("""
                        Inline a static final constant: replace one or all references with its
                        initializer expression, and optionally remove the field declaration.
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file    = (String) args.get("file");
                        boolean allOcc = Boolean.TRUE.equals(args.get("all_occurrences"));
                        boolean removeDecl = Boolean.TRUE.equals(args.get("remove_declaration"));

                        String source = Files.readString(Path.of(file));
                        int offset    = resolveOffset(args, source, Path.of(file).getFileName().toString());
                        String result = JdtInliner.inlineConstant(
                                source, Path.of(file).getFileName().toString(), offset, allOcc, removeDecl);
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

    static List<FileChange> executeRename(Map<String, Object> args)
            throws Exception {
        String projectRoot = (String) args.get("project_root");
        String file        = (String) args.get("file");
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
        int offset    = resolveOffset(args, source, sourceFile.getFileName().toString());

        return JdtRenamer.rename(ProjectDetector.detect(root), sourceFile, offset, newName);
    }

    /**
     * Resolves a position or name-based locator from MCP args to a char offset.
     * Accepts {@code line}+{@code column} (classic) or name fields:
     * {@code method}, {@code field}, {@code type}, {@code variable}, {@code parameter}
     * (with optional {@code class} scope qualifier).
     */
    static int resolveOffset(Map<String, Object> args, String source, String unitName) {
        return LocatorResolver.resolve(buildLocatorFromArgs(args), source, unitName);
    }

    private static Locator buildLocatorFromArgs(Map<String, Object> args) {
        boolean hasLine      = args.containsKey("line");
        boolean hasCol       = args.containsKey("column");
        boolean hasMethod    = args.containsKey("method");
        boolean hasField     = args.containsKey("field");
        boolean hasType      = args.containsKey("type");
        boolean hasParameter = args.containsKey("parameter");
        boolean hasVariable  = args.containsKey("variable");

        boolean hasPosition = hasLine || hasCol;
        boolean hasName     = hasMethod || hasField || hasType || hasParameter || hasVariable;

        if (hasCol && !hasLine)
            throw new IllegalArgumentException("'column' requires 'line' to also be specified.");

        if (hasPosition && hasName) {
            throw new IllegalArgumentException(
                    "Specify either a position (line / line+column) OR a name-based locator (method/field/type/variable/parameter), not both.");
        }
        if (!hasPosition && !hasName) {
            throw new IllegalArgumentException(
                    "Specify either a position (line, or line+column) or a name-based locator (method, field, type, variable, or parameter).");
        }

        if (hasPosition) {
            if (!hasCol)
                return new Locator.LineOnly(((Number) args.get("line")).intValue());
            return new Locator.Position(
                    ((Number) args.get("line")).intValue(),
                    ((Number) args.get("column")).intValue());
        }

        String className = (String) args.get("class");

        if (hasParameter) {
            if (!hasMethod) {
                throw new IllegalArgumentException("'parameter' requires 'method' to also be specified.");
            }
            return new Locator.ParameterInMethod((String) args.get("method"), (String) args.get("parameter"));
        }

        if (hasVariable) {
            if (!hasMethod)
                throw new IllegalArgumentException(
                        "'variable' requires 'method' to also be specified " +
                        "(local variables can share names across methods).");
            return new Locator.VariableName((String) args.get("variable"), (String) args.get("method"));
        }

        int kindCount = (hasMethod ? 1 : 0) + (hasField ? 1 : 0) + (hasType ? 1 : 0);
        if (kindCount > 1) {
            throw new IllegalArgumentException("Specify only one of: method, field, or type.");
        }

        if (hasMethod)   return new Locator.MethodName((String) args.get("method"), className);
        if (hasField)    return new Locator.FieldName((String) args.get("field"), className);
        if (hasType)     return new Locator.TypeName((String) args.get("type"));

        throw new IllegalArgumentException("No valid locator found in arguments.");
    }

    // -------------------------------------------------------------------------
    // Formatting helpers
    // -------------------------------------------------------------------------

    static String formatPreview(Map<Path, String> changed) {
        if (changed.isEmpty()) return "No changes.";
        String names = changed.keySet().stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.joining(", "));
        String content = changed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "\n=== " + e.getKey().getFileName() + " ===\n"
                        + e.getValue().stripTrailing() + "\n")
                .collect(Collectors.joining());
        return "Dry run — no files written.\n"
                + "Would change (" + changed.size() + "): " + names + "\n"
                + content;
    }

    static String formatPreview(List<FileChange> changed) {
        if (changed.isEmpty()) return "No changes.";
        String names = changed.stream()
                .map(fc -> fc.newPath().getFileName().toString())
                .sorted()
                .collect(Collectors.joining(", "));
        String content = changed.stream()
                .sorted(Comparator.comparing(fc -> fc.newPath().toString()))
                .map(fc -> "\n=== " + fc.newPath().getFileName() + " ===\n"
                        + fc.newSource().stripTrailing() + "\n")
                .collect(Collectors.joining());
        return "Dry run — no files written.\n"
                + "Would change (" + changed.size() + "): " + names + "\n"
                + content;
    }

    static String formatSummary(List<FileChange> changed) {
        if (changed.isEmpty()) return "No changes.";
        String content = changed.stream()
                .sorted(Comparator.comparing(fc -> fc.newPath().toString()))
                .map(fc -> fc.pathChanged()
                        ? "  " + fc.oldPath().getFileName() + " → " + fc.newPath().getFileName() + "\n"
                        : "  " + fc.newPath().getFileName() + "\n")
                .collect(Collectors.joining());
        return "Renamed in " + changed.size() + " file(s):\n" + content;
    }

    // -------------------------------------------------------------------------
    // Schema + result helpers
    // -------------------------------------------------------------------------

    private static Map<String, Object> renameSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("project_root", Map.of("type", "string",
                                "description", "Absolute path to the project root (Maven or Gradle).")),
                        Map.entry("file", Map.of("type", "string",
                                "description", "Source file — absolute or relative to project_root.")),
                        Map.entry("line", Map.of("type", "integer",
                                "description", "1-based line number. Use with 'column' OR use a name-based locator instead.")),
                        Map.entry("column", Map.of("type", "integer",
                                "description", "1-based column number. Use with 'line' OR use a name-based locator instead.")),
                        Map.entry("method", Map.of("type", "string",
                                "description", "Name-based locator for a method, e.g. \"add\" or \"add(int,int)\" for overloads.")),
                        Map.entry("field", Map.of("type", "string",
                                "description", "Name-based locator for a field, e.g. \"amount\".")),
                        Map.entry("type", Map.of("type", "string",
                                "description", "Name-based locator for a type (class/interface/enum), e.g. \"OrderService\".")),
                        Map.entry("class", Map.of("type", "string",
                                "description", "Optional: scope name-based locator to a specific class when the file contains multiple types.")),
                        Map.entry("refactoring", Map.of("type", "string",
                                "description", "Refactoring type. Currently supported: \"rename\".")),
                        Map.entry("new_name", Map.of("type", "string",
                                "description", "New name for the symbol."))
                ),
                "required", List.of("project_root", "file", "refactoring", "new_name")
        );
    }

    // -------------------------------------------------------------------------
    // Tool: pull_up_method
    // -------------------------------------------------------------------------

    static SyncToolSpecification pullUpMethod() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("pull_up_method", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("project_root", Map.of("type", "string", "description", "Absolute project root (Maven or Gradle).")),
                                Map.entry("file",         Map.of("type", "string", "description", "Absolute path to the .java file containing the subclass.")),
                                Map.entry("line",         Map.of("type", "integer", "description", "1-based line of the method to pull up. Use with 'column' OR use 'method' name-based locator.")),
                                Map.entry("column",       Map.of("type", "integer", "description", "1-based column of the method name. Use with 'line'.")),
                                Map.entry("method",       Map.of("type", "string",  "description", "Name-based locator: method name, e.g. \"speak\" or \"speak(int)\".")),
                                Map.entry("class",        Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types."))
                        ),
                        "required", List.of("project_root", "file")))
                        .description("""
                        Pull a method up from a subclass to its direct superclass.
                        The superclass must be in the project source roots.
                        Returns new source for both the subclass and the superclass.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file = (String) args.get("file");
                        String source = java.nio.file.Files.readString(Path.of(file));
                        int offset    = resolveOffset(args, source, Path.of(file).getFileName().toString());
                        var changed   = JdtPullUpMethod.pullUp(
                                ProjectDetector.detect(
                                        Path.of((String) args.get("project_root"))),
                                Path.of(file), offset);
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

    // -------------------------------------------------------------------------
    // Tool: push_down_method
    // -------------------------------------------------------------------------

    static SyncToolSpecification pushDownMethod() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("push_down_method", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("project_root", Map.of("type", "string", "description", "Absolute project root (Maven or Gradle).")),
                                Map.entry("file",         Map.of("type", "string", "description", "Absolute path to the .java file containing the superclass.")),
                                Map.entry("line",         Map.of("type", "integer", "description", "1-based line of the method to push down. Use with 'column' OR use 'method' name-based locator.")),
                                Map.entry("column",       Map.of("type", "integer", "description", "1-based column of the method name. Use with 'line'.")),
                                Map.entry("method",       Map.of("type", "string",  "description", "Name-based locator: method name, e.g. \"speak\" or \"speak(int)\".")),
                                Map.entry("class",        Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types."))
                        ),
                        "required", List.of("project_root", "file")))
                        .description("""
                        Push a method down from a class to all its direct subclasses in the project.
                        The method is removed from the superclass and added to every subclass found.
                        Subclasses are discovered by scanning for 'extends ClassName' in source files.
                        Returns new source for the superclass and all modified subclasses.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file = (String) args.get("file");
                        String source = java.nio.file.Files.readString(Path.of(file));
                        int offset    = resolveOffset(args, source, Path.of(file).getFileName().toString());
                        var changed   = JdtPushDownMethod.pushDown(
                                ProjectDetector.detect(
                                        Path.of((String) args.get("project_root"))),
                                Path.of(file), offset);
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

    // Tool: move_method

    static SyncToolSpecification moveMethod() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_method", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("project_root",  Map.of("type", "string", "description", "Absolute project root (Maven or Gradle).")),
                                Map.entry("file",          Map.of("type", "string", "description", "Absolute path to the .java file containing the method to move.")),
                                Map.entry("target_class",  Map.of("type", "string", "description", "Simple name of the target class, e.g. \"Report\".")),
                                Map.entry("line",          Map.of("type", "integer", "description", "1-based line of the method to move. Use with 'column' OR use 'method' name-based locator.")),
                                Map.entry("column",        Map.of("type", "integer", "description", "1-based column of the method name. Use with 'line'.")),
                                Map.entry("method",        Map.of("type", "string",  "description", "Name-based locator: method name, e.g. \"format\" or \"format(Report)\".")),
                                Map.entry("class",         Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types."))
                        ),
                        "required", List.of("project_root", "file", "target_class")))
                        .description("""
                        Move a method from one class to another class within the project.
                        The method is removed from the source class and added to the target class.
                        Target class is located by simple name within the project source roots.
                        Call sites in other files are not updated.
                        Returns new source for both the target file and the source file.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        String file = (String) args.get("file");
                        String targetClass = (String) args.get("target_class");
                        String source = java.nio.file.Files.readString(Path.of(file));
                        int offset    = resolveOffset(args, source, Path.of(file).getFileName().toString());
                        var changed   = JdtMoveMethod.moveMethod(
                                ProjectDetector.detect(
                                        Path.of((String) args.get("project_root"))),
                                Path.of(file), offset, targetClass);
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

    // Tool: pull_up_field

    static SyncToolSpecification pullUpField() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("pull_up_field", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("project_root", Map.of("type", "string",  "description", "Absolute path to the project root (Maven or Gradle).")),
                                Map.entry("file",         Map.of("type", "string",  "description", "Source file path relative to project_root")),
                                Map.entry("line",         Map.of("type", "integer", "description", "1-based line of the field to pull up. Use with 'column' OR use 'field' name-based locator.")),
                                Map.entry("column",       Map.of("type", "integer", "description", "1-based column inside the field declaration. Use with 'line'.")),
                                Map.entry("field",        Map.of("type", "string",  "description", "Name-based locator: field name, e.g. \"amount\".")),
                                Map.entry("class",        Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types."))
                        ),
                        "required", List.of("project_root", "file")
                )).build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path root = Path.of((String) args.get("project_root"));
                        Path file = root.resolve((String) args.get("file"));

                        String source = java.nio.file.Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());

                        var changed = JdtPullUpField.pullUp(
                                ProjectDetector.detect(root), file, offset);
                        return ok(formatPreview(changed));
                    } catch (IllegalArgumentException e) {
                        return error(e.getMessage());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // Tool: push_down_field

    static SyncToolSpecification pushDownField() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("push_down_field", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("project_root", Map.of("type", "string",  "description", "Absolute path to the project root (Maven or Gradle).")),
                                Map.entry("file",         Map.of("type", "string",  "description", "Source file path relative to project_root")),
                                Map.entry("line",         Map.of("type", "integer", "description", "1-based line of the field to push down. Use with 'column' OR use 'field' name-based locator.")),
                                Map.entry("column",       Map.of("type", "integer", "description", "1-based column inside the field declaration. Use with 'line'.")),
                                Map.entry("field",        Map.of("type", "string",  "description", "Name-based locator: field name, e.g. \"amount\".")),
                                Map.entry("class",        Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types."))
                        ),
                        "required", List.of("project_root", "file")
                )).build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path root = Path.of((String) args.get("project_root"));
                        Path file = root.resolve((String) args.get("file"));

                        String source = java.nio.file.Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());

                        var changed = JdtPushDownField.pushDown(
                                ProjectDetector.detect(root), file, offset);
                        return ok(formatPreview(changed));
                    } catch (IllegalArgumentException e) {
                        return error(e.getMessage());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // Tool: introduce_static_factory

    static SyncToolSpecification introduceStaticFactory() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_static_factory", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("project_root",            Map.of("type", "string",  "description", "Absolute path to the project root (Maven or Gradle).")),
                                Map.entry("file",                    Map.of("type", "string",  "description", "Source file path relative to project_root")),
                                Map.entry("line",                    Map.of("type", "integer", "description", "1-based line of the constructor. Use with 'column' OR use 'method' name-based locator.")),
                                Map.entry("column",                  Map.of("type", "integer", "description", "1-based column inside the constructor. Use with 'line'.")),
                                Map.entry("method",                  Map.of("type", "string",  "description", "Name-based locator: constructor class name, e.g. \"Calculator\" or \"Calculator(int,int)\".")),
                                Map.entry("factory_method_name",     Map.of("type", "string",  "description", "Simple name for the new factory method, e.g. 'of' or 'create'")),
                                Map.entry("make_constructor_private",Map.of("type", "boolean", "description", "When true, changes the constructor visibility to private"))
                        ),
                        "required", List.of("project_root", "file", "factory_method_name")
                )).build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path root   = Path.of((String) args.get("project_root"));
                        Path file   = root.resolve((String) args.get("file"));
                        String name = (String) args.get("factory_method_name");
                        boolean makePrivate = args.getOrDefault("make_constructor_private", false) instanceof Boolean b && b;

                        String source = java.nio.file.Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());

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

    // Tool: introduce_parameter_object

    static SyncToolSpecification introduceParameterObject() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_parameter_object", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("project_root",      Map.of("type", "string",  "description", "Absolute path to the project root (Maven or Gradle).")),
                                Map.entry("file",              Map.of("type", "string",  "description", "Source file path relative to project_root")),
                                Map.entry("line",              Map.of("type", "integer", "description", "1-based line of the method declaration. Use with 'column' OR use 'method' name-based locator.")),
                                Map.entry("column",            Map.of("type", "integer", "description", "1-based column inside the method declaration. Use with 'line'.")),
                                Map.entry("method",            Map.of("type", "string",  "description", "Name-based locator: method name, e.g. \"process\" or \"process(String,int)\".")),
                                Map.entry("class",             Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types.")),
                                Map.entry("param_names",       Map.of("type", "array", "items", Map.of("type", "string"),
                                                                      "description", "Names of the contiguous parameters to group (>=2)")),
                                Map.entry("class_name",        Map.of("type", "string",  "description", "Simple name for the new parameter-object class")),
                                Map.entry("param_object_name", Map.of("type", "string",  "description", "Name for the new parameter in the method (optional, defaults to lower-camel of class_name)")),
                                Map.entry("as_record", Map.of("type", "boolean", "description", "If true, generate a record instead of a plain class (Java 16+)."))
                        ),
                        "required", List.of("project_root", "file", "param_names", "class_name")
                )).build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path root = Path.of((String) args.get("project_root"));
                        Path file = root.resolve((String) args.get("file"));
                        @SuppressWarnings("unchecked")
                        List<String> paramNames = (List<String>) args.get("param_names");
                        String className = (String) args.get("class_name");
                        String paramObjName = args.containsKey("param_object_name")
                                ? (String) args.get("param_object_name")
                                : Character.toLowerCase(className.charAt(0)) + className.substring(1);

                        String source = java.nio.file.Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());

                        boolean asRecord = Boolean.TRUE.equals(args.get("as_record"));
                        var changed = JdtIntroduceParameterObject.introduce(
                                ProjectDetector.detect(root),
                                file, offset, paramNames, className, paramObjName, asRecord);
                        return ok(formatPreview(changed));
                    } catch (IllegalArgumentException e) {
                        return error(e.getMessage());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // Tool: convert_to_record

    static SyncToolSpecification convertToRecord() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_to_record", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "project_root", Map.of("type", "string",
                                        "description", "Absolute path to the project root (Maven or Gradle)."),
                                "file", Map.of("type", "string",
                                        "description", "Source file path relative to project_root")
                        ),
                        "required", List.of("project_root", "file")
                )).build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path root = Path.of((String) args.get("project_root"));
                        Path file = root.resolve((String) args.get("file"));
                        var changed = JdtConvertToRecord.convertToRecord(
                                ProjectDetector.detect(root), file);
                        return ok(formatPreview(changed));
                    } catch (IllegalArgumentException e) {
                        return error(e.getMessage());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: change_method_signature
    // -------------------------------------------------------------------------

    static SyncToolSpecification changeMethodSignature() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("change_method_signature", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("project_root",   Map.of("type", "string",  "description", "Absolute project root (Maven or Gradle).")),
                                Map.entry("file",           Map.of("type", "string",  "description", "Source file containing the method declaration.")),
                                Map.entry("line",           Map.of("type", "integer", "description", "1-based line of the method name.")),
                                Map.entry("column",         Map.of("type", "integer", "description", "1-based column of the method name.")),
                                Map.entry("method",         Map.of("type", "string",  "description", "Name-based locator: method name, e.g. \"convert\" or \"convert(int,String)\".")),
                                Map.entry("class",          Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types.")),
                                Map.entry("new_return_type",Map.of("type", "string",  "description", "New return type source text (e.g. \"double\"). Omit to leave unchanged.")),
                                Map.entry("param_order",    Map.of("type", "array", "items", Map.of("type", "integer"),
                                        "description", "New parameter order as 0-based indices (e.g. [1,0] swaps two params). Omit to leave unchanged."))
                        ),
                        "required", List.of("project_root", "file")))
                        .description("""
                        Change a method's return type and/or reorder its parameters project-wide.
                        param_order: integer array where param_order[i] is the original index of the
                        parameter that should appear at position i. Call sites are updated when
                        parameters are reordered. Returns changed file contents; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path root = Path.of((String) args.get("project_root"));
                        Path file = root.resolve((String) args.get("file"));
                        String source = Files.readString(file);
                        int offset = resolveOffset(args, source, file.getFileName().toString());
                        String newReturnType = (String) args.get("new_return_type");
                        int[] paramOrder = null;
                        if (args.containsKey("param_order")) {
                            @SuppressWarnings("unchecked")
                            List<Number> orderList = (List<Number>) args.get("param_order");
                            paramOrder = orderList.stream().mapToInt(Number::intValue).toArray();
                        }
                        var changed = JdtChangeMethodSignature.changeSignature(
                                ProjectDetector.detect(root), file, offset, newReturnType, paramOrder);
                        return ok(formatPreview(changed));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: encapsulate_field
    // -------------------------------------------------------------------------

    static SyncToolSpecification encapsulateField() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("encapsulate_field", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("project_root",    Map.of("type", "string",  "description", "Absolute project root (Maven or Gradle).")),
                                Map.entry("file",            Map.of("type", "string",  "description", "Source file containing the field declaration.")),
                                Map.entry("line",            Map.of("type", "integer", "description", "1-based line of the field name.")),
                                Map.entry("column",          Map.of("type", "integer", "description", "1-based column of the field name.")),
                                Map.entry("field",           Map.of("type", "string",  "description", "Name-based locator: field name, e.g. \"name\".")),
                                Map.entry("class",           Map.of("type", "string",  "description", "Optional: scope to a specific class when the file contains multiple types.")),
                                Map.entry("generate_setter", Map.of("type", "boolean", "description", "Also generate a setter and rewrite write access sites (default false)."))
                        ),
                        "required", List.of("project_root", "file")))
                        .description("""
                        Make a public field private, generate a getter (and optional setter),
                        and rewrite all read access sites (and write sites if generate_setter=true)
                        across the project. Returns changed file contents; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path root = Path.of((String) args.get("project_root"));
                        Path file = root.resolve((String) args.get("file"));
                        String source = Files.readString(file);
                        int offset = resolveOffset(args, source, file.getFileName().toString());
                        boolean generateSetter = Boolean.TRUE.equals(args.get("generate_setter"));
                        var changed = JdtEncapsulateField.encapsulateField(
                                ProjectDetector.detect(root), file, offset, generateSetter);
                        return ok(formatPreview(changed));
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: decompose_conditional
    // -------------------------------------------------------------------------

    static SyncToolSpecification decomposeConditional() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("decompose_conditional", Map.of(
                        "type", "object",
                        "properties", Map.ofEntries(
                                Map.entry("file",        Map.of("type", "string",  "description", "Absolute path to the source file.")),
                                Map.entry("line",        Map.of("type", "integer", "description", "1-based line within the boolean condition.")),
                                Map.entry("column",      Map.of("type", "integer", "description", "1-based column within the condition.")),
                                Map.entry("method_name", Map.of("type", "string",  "description", "Name for the extracted boolean method (e.g. 'isAdultPremium')."))
                        ),
                        "required", List.of("file", "method_name")))
                        .description("""
                        Extract a compound boolean condition from an if/while/for statement
                        into a private boolean method. Single-file operation.
                        Returns the modified source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path file = Path.of((String) args.get("file"));
                        String source = Files.readString(file);
                        String methodName = (String) args.get("method_name");
                        int offset = resolveOffset(args, source, file.getFileName().toString());
                        String result = JdtDecomposeConditional.decomposeConditional(
                                source, file.getFileName().toString(), offset, methodName);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // Tool: convert_anonymous_to_nested
    static SyncToolSpecification convertAnonymousToNested() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_anonymous_to_nested", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file",              Map.of("type", "string", "description", "Source file (absolute or relative to project_root)"),
                                "line",              Map.of("type", "integer", "description", "1-based line inside the anonymous class"),
                                "column",            Map.of("type", "integer", "description", "1-based column inside the anonymous class"),
                                "nested_class_name", Map.of("type", "string",  "description", "Simple name for the new nested class")
                        ),
                        "required", List.of("file", "line", "column", "nested_class_name")
                ))
                .description("Convert an anonymous class at the given position to a private named nested class.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path file         = Path.of((String) args.get("file"));
                        String source     = Files.readString(file);
                        int offset        = resolveOffset(args, source, file.getFileName().toString());
                        String nestedName = (String) args.get("nested_class_name");
                        String result     = JdtConvertAnonymousToNested.convert(
                                source, file.getFileName().toString(), offset, nestedName);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // Tool: introduce_indirection
    static SyncToolSpecification introduceIndirection() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_indirection", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file",                   Map.of("type", "string",  "description", "Source file containing the method (absolute path)"),
                                "line",                   Map.of("type", "integer", "description", "1-based line inside the method"),
                                "column",                 Map.of("type", "integer", "description", "1-based column inside the method"),
                                "indirection_method_name", Map.of("type", "string", "description", "Name for the new static wrapper method")
                        ),
                        "required", List.of("file", "line", "column", "indirection_method_name")
                ))
                .description("Add a public static indirection (wrapper) method that delegates to the method at the given position.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path file    = Path.of((String) args.get("file"));
                        String source = Files.readString(file);
                        int offset   = resolveOffset(args, source, file.getFileName().toString());
                        String name  = (String) args.get("indirection_method_name");
                        String result = JdtIntroduceIndirection.introduceIndirection(
                                source, file.getFileName().toString(), offset, name);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // Tool: move_static_member
    static SyncToolSpecification moveStaticMember() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_static_member", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "project_root",   Map.of("type", "string",  "description", "Absolute path to the Maven project root"),
                                "file",           Map.of("type", "string",  "description", "Source file containing the static member (absolute path)"),
                                "line",           Map.of("type", "integer", "description", "1-based line of the static member"),
                                "column",         Map.of("type", "integer", "description", "1-based column of the static member"),
                                "target_class",   Map.of("type", "string",  "description", "Simple name of the target class")
                        ),
                        "required", List.of("project_root", "file", "line", "column", "target_class")
                ))
                .description("Move a static method or static field to another class and update call sites.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path projectRoot  = Path.of((String) args.get("project_root"));
                        Path file         = Path.of((String) args.get("file"));
                        String source     = Files.readString(file);
                        int offset        = resolveOffset(args, source, file.getFileName().toString());
                        String target     = (String) args.get("target_class");
                        var project = new com.github.pfichtner.project.MavenProject(projectRoot);
                        java.util.Map<java.nio.file.Path, String> changed =
                                JdtMoveStaticMember.moveStaticMember(project, file, offset, target);
                        StringBuilder sb = new StringBuilder();
                        changed.forEach((p, src) ->
                                sb.append("=== ").append(p.getFileName()).append(" ===\n").append(src).append("\n"));
                        return ok(sb.toString());
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // Tool: promote_to_field
    static SyncToolSpecification promoteToField() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("promote_to_field", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file",   Map.of("type", "string",  "description", "Source file containing the local variable (absolute path)"),
                                "line",   Map.of("type", "integer", "description", "1-based line of the local variable declaration"),
                                "column", Map.of("type", "integer", "description", "1-based column of the local variable declaration")
                        ),
                        "required", List.of("file", "line", "column")
                ))
                .description("Promote a local variable declaration to a private instance field of the enclosing class.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path file    = Path.of((String) args.get("file"));
                        String source = Files.readString(file);
                        int offset   = resolveOffset(args, source, file.getFileName().toString());
                        String result = JdtPromoteToField.promote(
                                source, file.getFileName().toString(), offset);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // Tool: convert_nested_to_top_level
    static SyncToolSpecification convertNestedToTopLevel() {
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_nested_to_top_level", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "file",   Map.of("type", "string",  "description", "Source file containing the nested type (absolute path)"),
                                "line",   Map.of("type", "integer", "description", "1-based line inside the nested type"),
                                "column", Map.of("type", "integer", "description", "1-based column inside the nested type")
                        ),
                        "required", List.of("file", "line", "column")
                ))
                .description("Convert a nested (member) type to a top-level type. Returns both the modified outer source and the new type's source.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        Map<String, Object> args = request.arguments();
                        Path file   = Path.of((String) args.get("file"));
                        String source = Files.readString(file);
                        int offset  = resolveOffset(args, source, file.getFileName().toString());
                        JdtConvertNestedToTopLevel.Result result =
                                JdtConvertNestedToTopLevel.convert(
                                        source, file.getFileName().toString(), offset);
                        String out = "=== " + file.getFileName() + " (modified) ===\n"
                                + result.outerSource()
                                + "\n=== " + result.newTypeName() + ".java (new file) ===\n"
                                + result.newTypeSource();
                        return ok(out);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
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
