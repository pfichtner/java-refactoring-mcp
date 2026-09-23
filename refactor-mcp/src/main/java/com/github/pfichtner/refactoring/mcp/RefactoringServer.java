package com.github.pfichtner.refactoring.mcp;

import static com.github.pfichtner.refactoring.mcp.Property.AS_RECORD;
import static com.github.pfichtner.refactoring.mcp.Property.CASCADE;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.CLASS_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.CONST_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.END_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.END_LINE;
import static com.github.pfichtner.refactoring.mcp.Property.FACTORY_METHOD_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.FIELD;
import static com.github.pfichtner.refactoring.mcp.Property.FILE;
import static com.github.pfichtner.refactoring.mcp.Property.GENERATE_SETTER;
import static com.github.pfichtner.refactoring.mcp.Property.INDIRECTION_METHOD_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.INTERFACE_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.LINE;
import static com.github.pfichtner.refactoring.mcp.Property.MAKE_CONSTRUCTOR_PRIVATE;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.METHOD_NAMES;
import static com.github.pfichtner.refactoring.mcp.Property.NESTED_CLASS_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.NEW_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.NEW_PACKAGE;
import static com.github.pfichtner.refactoring.mcp.Property.NEW_RETURN_TYPE;
import static com.github.pfichtner.refactoring.mcp.Property.OLD_PACKAGE;
import static com.github.pfichtner.refactoring.mcp.Property.PARAMETER;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_NAMES;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_OBJECT_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_ORDER;
import static com.github.pfichtner.refactoring.mcp.Property.PARAM_TYPE;
import static com.github.pfichtner.refactoring.mcp.Property.PROJECT_ROOT;
import static com.github.pfichtner.refactoring.mcp.Property.REFACTORING;
import static com.github.pfichtner.refactoring.mcp.Property.REMOVE_DECLARATION;
import static com.github.pfichtner.refactoring.mcp.Property.REPLACE_ALL;
import static com.github.pfichtner.refactoring.mcp.Property.START_COLUMN;
import static com.github.pfichtner.refactoring.mcp.Property.START_LINE;
import static com.github.pfichtner.refactoring.mcp.Property.SUPERCLASS_NAME;
import static com.github.pfichtner.refactoring.mcp.Property.TARGET_CLASS;
import static com.github.pfichtner.refactoring.mcp.Property.TYPE;
import static com.github.pfichtner.refactoring.mcp.Property.VARIABLE;
import static com.github.pfichtner.refactoring.mcp.Property.VAR_NAME;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
import com.github.pfichtner.refactoring.locator.LocatorResolver;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

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
        Options opts = renameOptions();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("analyze_refactoring", opts.toSchema())
                        .description("""
                        Preview what a refactoring would change without modifying files on disk.
                        Returns the new source of every file that would be affected.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var changed = executeRename(opts.reader(request.arguments()));
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

    // -------------------------------------------------------------------------
    // Tool: extract_method
    // -------------------------------------------------------------------------

    static SyncToolSpecification extractMethod() {
        Options opts = Options.of(FILE, START_LINE, START_COLUMN, END_LINE, END_COLUMN, METHOD_NAME);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_method", opts.toSchema())
                        .description("""
                        Extract statements into a new method.
                        Provide start/end line+column (1-based).
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args      = opts.reader(request.arguments());
                        Path file     = args.getPath(FILE);
                        int startLine = args.getInt(START_LINE);
                        int startCol  = args.getInt(START_COLUMN);
                        int endLine   = args.getInt(END_LINE);
                        int endCol    = args.getInt(END_COLUMN);
                        String methodName = args.getString(METHOD_NAME);

                        String source  = Files.readString(file);
                        int selStart   = JdtRenamer.toOffset(source, startLine, startCol);
                        int selEnd     = JdtRenamer.toOffset(source, endLine, endCol);
                        String result  = JdtExtractor.extractMethod(
                                source, file.getFileName().toString(),
                                selStart, selEnd - selStart, methodName);
                        return ok(result);
                    } catch (Exception e) {
                        return error(e.getMessage());
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: rename_package
    // -------------------------------------------------------------------------

    static SyncToolSpecification renamePackage() {
        Options opts = Options.of(PROJECT_ROOT, OLD_PACKAGE, NEW_PACKAGE);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("rename_package", opts.toSchema())
                        .description("""
                        Rename a package across the project.
                        Updates package declarations, single-class imports, and wildcard imports.
                        Returns a list of changed files with new sources and new paths.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = opts.reader(request.arguments());
                        var result = JdtRenamePackage.renamePackage(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                args.getString(OLD_PACKAGE),
                                args.getString(NEW_PACKAGE));
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
        Options opts = Options.of(PROJECT_ROOT, FILE, NEW_PACKAGE);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_class", opts.toSchema())
                        .description("""
                        Move a Java class to a new package.
                        Updates the package declaration and all explicit imports in the project.
                        Returns: new class source, new canonical file path, updated import files.
                        Does not write to disk or delete the original file.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args       = opts.reader(request.arguments());
                        Path file         = args.getPath(FILE);
                        String newPackage = args.getString(NEW_PACKAGE);
                        var result = JdtMoveClass.moveClass(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                file, newPackage);

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
        Options opts = Options.builder().add(METHOD_NAMES).addRequired(FILE, SUPERCLASS_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_superclass", opts.toSchema())
                        .description("""
                        Extract an abstract superclass from selected methods of a class.
                        Returns the modified class source and the new superclass source.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = opts.reader(request.arguments());
                        Path file            = args.getPath(FILE);
                        String superName     = args.getString(SUPERCLASS_NAME);
                        List<String> methods = args.getStringList(METHOD_NAMES);
                        String source = Files.readString(file);
                        var result = JdtExtractSuperclass.extractSuperclass(
                                source, file.getFileName().toString(), superName, methods);
                        String out = "=== " + file.getFileName() + " (modified) ===\n"
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
        Options opts = Options.builder().add(METHOD_NAMES).addRequired(FILE, INTERFACE_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_interface", opts.toSchema())
                        .description("""
                        Extract a new interface from the public methods of a class.
                        Returns the modified class source and the new interface source.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args = opts.reader(request.arguments());
                        Path file             = args.getPath(FILE);
                        String interfaceName  = args.getString(INTERFACE_NAME);
                        List<String> methods  = args.getStringList(METHOD_NAMES);

                        String source = Files.readString(file);
                        var result = JdtExtractInterface.extractInterface(
                                source, file.getFileName().toString(),
                                interfaceName, methods);

                        String out = "=== " + file.getFileName() + " (modified) ===\n"
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
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, PARAMETER, CLASS).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("remove_param", opts.toSchema())
                        .description("""
                        Remove an unused parameter from a method and the corresponding argument
                        from every call site in the project.
                        Returns a map of filename → new source for each changed file.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        Path file   = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());
                        var changed = JdtRemoveParam.removeParam(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                file, offset);
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
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS, CASCADE).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("remove_method", opts.toSchema())
                        .description("""
                        Remove a method from a class or interface.
                        When cascade is true (the default), also removes every overriding method
                        in subclasses and implementing method in implementing classes.
                        Returns a map of filename → new source for each changed file.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args    = opts.reader(request.arguments());
                        Path file    = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());
                        boolean cascade = args.getBoolean(CASCADE, true);
                        var changed = JdtRemoveMethod.removeMethod(
                                ProjectDetector.detect(args.getPath(PROJECT_ROOT)),
                                file, offset, cascade);
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
        Options opts = Options.builder().add(PARAM_TYPE).addRequired(PROJECT_ROOT, FILE, START_LINE, START_COLUMN, END_LINE, END_COLUMN, PARAM_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_param", opts.toSchema())
                        .description("""
                        Promote an expression to a parameter.
                        Updates the method signature and all call sites in the project.
                        Returns a map of filename → new source for each changed file.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args      = opts.reader(request.arguments());
                        Path file     = args.getPath(FILE);
                        int startLine = args.getInt(START_LINE);
                        int startCol  = args.getInt(START_COLUMN);
                        int endLine   = args.getInt(END_LINE);
                        int endCol    = args.getInt(END_COLUMN);
                        String paramName = args.getString(PARAM_NAME);
                        String paramType = args.getString(PARAM_TYPE); // nullable

                        String source  = Files.readString(file);
                        int selStart   = JdtRenamer.toOffset(source, startLine, startCol);
                        int selEnd     = JdtRenamer.toOffset(source, endLine, endCol);

                        var changed = JdtIntroduceParam.introduceParam(
                                ProjectDetector.detect(args.getPath(PROJECT_ROOT)),
                                file, selStart, selEnd - selStart, paramName, paramType);

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
        Options opts = Options.builder().add(REPLACE_ALL).addRequired(FILE, START_LINE, START_COLUMN, END_LINE, END_COLUMN, CONST_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_constant", opts.toSchema())
                        .description("""
                        Extract an expression into a private static final constant at class level.
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args       = opts.reader(request.arguments());
                        Path file      = args.getPath(FILE);
                        int startLine  = args.getInt(START_LINE);
                        int startCol   = args.getInt(START_COLUMN);
                        int endLine    = args.getInt(END_LINE);
                        int endCol     = args.getInt(END_COLUMN);
                        String constName  = args.getString(CONST_NAME);
                        boolean replaceAll = args.getBoolean(REPLACE_ALL);

                        String source  = Files.readString(file);
                        int selStart   = JdtRenamer.toOffset(source, startLine, startCol);
                        int selEnd     = JdtRenamer.toOffset(source, endLine, endCol);
                        return ok(JdtExtractConstant.extractConstant(
                                source, file.getFileName().toString(),
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
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS, REMOVE_DECLARATION).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_method", opts.toSchema())
                        .description("""
                        Inline a method call at all call sites in the project.
                        Optionally removes the method declaration.
                        Returns new source for every changed file; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args      = opts.reader(request.arguments());
                        Path file     = args.getPath(FILE);
                        boolean removeDel = args.getBoolean(REMOVE_DECLARATION);
                        String source = Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());
                        var changed = JdtInlineMethod.inlineMethod(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                file, offset, removeDel);
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
        Options opts = Options.builder().add(REPLACE_ALL).addRequired(FILE, START_LINE, START_COLUMN, END_LINE, END_COLUMN, VAR_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("extract_variable", opts.toSchema())
                        .description("""
                        Extract an expression into a new local variable.
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args      = opts.reader(request.arguments());
                        Path file     = args.getPath(FILE);
                        int startLine = args.getInt(START_LINE);
                        int startCol  = args.getInt(START_COLUMN);
                        int endLine   = args.getInt(END_LINE);
                        int endCol    = args.getInt(END_COLUMN);
                        String varName    = args.getString(VAR_NAME);
                        boolean replaceAll = args.getBoolean(REPLACE_ALL);

                        String source  = Files.readString(file);
                        int selStart   = JdtRenamer.toOffset(source, startLine, startCol);
                        int selEnd     = JdtRenamer.toOffset(source, endLine, endCol);
                        String result  = JdtExtractVariable.extractVariable(
                                source, file.getFileName().toString(),
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
        Options opts = Options.builder().add(LINE, COLUMN, VARIABLE).addRequired(FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_variable", opts.toSchema())
                        .description("""
                        Inline a local variable: replace every use with its initializer expression
                        and remove the declaration. Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        Path file   = args.getPath(FILE);

                        String source = Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());
                        String result = JdtInliner.inlineVariable(
                                source, file.getFileName().toString(), offset);
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
        Options opts = Options.builder().add(LINE, COLUMN, FIELD, CLASS, REPLACE_ALL, REMOVE_DECLARATION).addRequired(FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("inline_constant", opts.toSchema())
                        .description("""
                        Inline a static final constant: replace one or all references with its
                        initializer expression, and optionally remove the field declaration.
                        Returns the rewritten source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args       = opts.reader(request.arguments());
                        Path file      = args.getPath(FILE);
                        boolean allOcc = args.getBoolean(REPLACE_ALL);
                        boolean removeDecl = args.getBoolean(REMOVE_DECLARATION);

                        String source = Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());
                        String result = JdtInliner.inlineConstant(
                                source, file.getFileName().toString(), offset, allOcc, removeDecl);
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

    static List<FileChange> executeRename(Options.Reader args)
            throws Exception {
        Path filePath      = args.getPath(FILE);
        String newName     = args.getString(NEW_NAME);
        String refactoring = args.getString(REFACTORING, "rename");

        if (!"rename".equals(refactoring)) {
            throw new IllegalArgumentException(
                    "Unsupported refactoring type: '" + refactoring
                    + "'. Supported: rename");
        }

        Path root = args.getPath(PROJECT_ROOT);
        Path sourceFile = filePath.isAbsolute()
                ? filePath
                : root.resolve(filePath);

        String source = Files.readString(sourceFile);
        int offset = resolveOffset(args, source, sourceFile.getFileName().toString());

        return JdtRenamer.rename(ProjectDetector.detect(root), sourceFile, offset, newName);
    }

    /**
     * Resolves a position or name-based locator from MCP args to a char offset.
     * Accepts {@code line}+{@code column} (classic) or name fields:
     * {@code method}, {@code field}, {@code type}, {@code variable}, {@code parameter}
     * (with optional {@code class} scope qualifier).
     */
    static int resolveOffset(Options.Reader args, String source, String unitName) {
        return LocatorResolver.resolve(args.getLocator(), source, unitName);
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

    private static Options renameOptions() {
        return Options.builder()
                .add(LINE, COLUMN, METHOD, FIELD, TYPE, CLASS)
                .addRequired(PROJECT_ROOT, FILE, REFACTORING, NEW_NAME)
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool: pull_up_method
    // -------------------------------------------------------------------------

    static SyncToolSpecification pullUpMethod() {
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("pull_up_method", opts.toSchema())
                        .description("""
                        Pull a method up from a subclass to its direct superclass.
                        The superclass must be in the project source roots.
                        Returns new source for both the subclass and the superclass.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        Path file   = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());
                        var changed   = JdtPullUpMethod.pullUp(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                file, offset);
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
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("push_down_method", opts.toSchema())
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
                        var args   = opts.reader(request.arguments());
                        Path file   = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());
                        var changed   = JdtPushDownMethod.pushDown(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                file, offset);
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
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS).addRequired(PROJECT_ROOT, FILE, TARGET_CLASS).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_method", opts.toSchema())
                        .description("""
                        Move a method from one class to another class within the project.
                        The method is removed from the source class and added to the target class.
                        Target class is identified by its fully-qualified name (e.g. com.example.Report).
                        Call sites in other files are not updated.
                        Returns new source for both the target file and the source file.
                        Does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args         = opts.reader(request.arguments());
                        Path file         = args.getPath(FILE);
                        String targetClass = args.getString(TARGET_CLASS);
                        String source     = Files.readString(file);
                        int offset        = resolveOffset(args, source, file.getFileName().toString());
                        var changed = JdtMoveMethod.moveMethod(
                                ProjectDetector.detect(
                                        args.getPath(PROJECT_ROOT)),
                                file, offset, targetClass);
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
        Options opts = Options.builder().add(LINE, COLUMN, FIELD, CLASS).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("pull_up_field", opts.toSchema()).build())
                .callHandler((exchange, request) -> {
                    try {
                        var args  = opts.reader(request.arguments());
                        Path root = args.getPath(PROJECT_ROOT);
                        Path file = root.resolve(args.getPath(FILE));

                        String source = Files.readString(file);
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
        Options opts = Options.builder().add(LINE, COLUMN, FIELD, CLASS).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("push_down_field", opts.toSchema()).build())
                .callHandler((exchange, request) -> {
                    try {
                        var args  = opts.reader(request.arguments());
                        Path root = args.getPath(PROJECT_ROOT);
                        Path file = root.resolve(args.getPath(FILE));

                        String source = Files.readString(file);
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
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, MAKE_CONSTRUCTOR_PRIVATE).addRequired(PROJECT_ROOT, FILE, FACTORY_METHOD_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_static_factory", opts.toSchema()).build())
                .callHandler((exchange, request) -> {
                    try {
                        var args    = opts.reader(request.arguments());
                        Path root   = args.getPath(PROJECT_ROOT);
                        Path file   = root.resolve(args.getPath(FILE));
                        String name = args.getString(FACTORY_METHOD_NAME);
                        boolean makePrivate = args.getBoolean(MAKE_CONSTRUCTOR_PRIVATE);

                        String source = Files.readString(file);
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
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS, PARAM_OBJECT_NAME, AS_RECORD).addRequired(PROJECT_ROOT, FILE, PARAM_NAMES, CLASS_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_parameter_object", opts.toSchema()).build())
                .callHandler((exchange, request) -> {
                    try {
                        var args  = opts.reader(request.arguments());
                        Path root = args.getPath(PROJECT_ROOT);
                        Path file = root.resolve(args.getPath(FILE));
                        List<String> paramNames = args.getStringList(PARAM_NAMES);
                        String className = args.getString(CLASS_NAME);
                        String paramObjName = args.has(PARAM_OBJECT_NAME)
                                ? args.getString(PARAM_OBJECT_NAME)
                                : Character.toLowerCase(className.charAt(0)) + className.substring(1);

                        String source = Files.readString(file);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());

                        boolean asRecord = args.getBoolean(AS_RECORD);
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
        Options opts = Options.of(PROJECT_ROOT, FILE);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_to_record", opts.toSchema()).build())
                .callHandler((exchange, request) -> {
                    try {
                        var args  = opts.reader(request.arguments());
                        Path root = args.getPath(PROJECT_ROOT);
                        Path file = root.resolve(args.getPath(FILE));
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
        Options opts = Options.builder().add(LINE, COLUMN, METHOD, CLASS, NEW_RETURN_TYPE, PARAM_ORDER).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("change_method_signature", opts.toSchema())
                        .description("""
                        Change a method's return type and/or reorder its parameters project-wide.
                        param_order: integer array where param_order[i] is the original index of the
                        parameter that should appear at position i. Call sites are updated when
                        parameters are reordered. Returns changed file contents; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args  = opts.reader(request.arguments());
                        Path root = args.getPath(PROJECT_ROOT);
                        Path file = root.resolve(args.getPath(FILE));
                        String source = Files.readString(file);
                        int offset = resolveOffset(args, source, file.getFileName().toString());
                        String newReturnType = args.getString(NEW_RETURN_TYPE);
                        int[] paramOrder     = args.getIntArray(PARAM_ORDER);
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
        Options opts = Options.builder().add(LINE, COLUMN, FIELD, CLASS, GENERATE_SETTER).addRequired(PROJECT_ROOT, FILE).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("encapsulate_field", opts.toSchema())
                        .description("""
                        Make a public field private, generate a getter (and optional setter),
                        and rewrite all read access sites (and write sites if generate_setter=true)
                        across the project. Returns changed file contents; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args  = opts.reader(request.arguments());
                        Path root = args.getPath(PROJECT_ROOT);
                        Path file = root.resolve(args.getPath(FILE));
                        String source = Files.readString(file);
                        int offset = resolveOffset(args, source, file.getFileName().toString());
                        boolean generateSetter = args.getBoolean(GENERATE_SETTER);
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
        Options opts = Options.builder().add(LINE, COLUMN).addRequired(FILE, METHOD_NAME).build();
        return SyncToolSpecification.builder()
                .tool(Tool.builder("decompose_conditional", opts.toSchema())
                        .description("""
                        Extract a compound boolean condition from an if/while/for statement
                        into a private boolean method. Single-file operation.
                        Returns the modified source; does not write to disk.
                        """)
                        .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args      = opts.reader(request.arguments());
                        Path file     = args.getPath(FILE);
                        String source = Files.readString(file);
                        String methodName = args.getString(METHOD_NAME);
                        int offset    = resolveOffset(args, source, file.getFileName().toString());
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
        Options opts = Options.of(FILE, LINE, COLUMN, NESTED_CLASS_NAME);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_anonymous_to_nested", opts.toSchema())
                .description("Convert an anonymous class at the given position to a private named nested class.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args          = opts.reader(request.arguments());
                        Path file         = args.getPath(FILE);
                        String source     = Files.readString(file);
                        int offset        = resolveOffset(args, source, file.getFileName().toString());
                        String nestedName = args.getString(NESTED_CLASS_NAME);
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
        Options opts = Options.of(FILE, LINE, COLUMN, INDIRECTION_METHOD_NAME);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("introduce_indirection", opts.toSchema())
                .description("Add a public static indirection (wrapper) method that delegates to the method at the given position.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args    = opts.reader(request.arguments());
                        Path file   = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset  = resolveOffset(args, source, file.getFileName().toString());
                        String name = args.getString(INDIRECTION_METHOD_NAME);
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
        Options opts = Options.of(PROJECT_ROOT, FILE, LINE, COLUMN, TARGET_CLASS);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("move_static_member", opts.toSchema())
                .description("Move a static method or static field to another class and update call sites.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args          = opts.reader(request.arguments());
                        Path projectRoot  = args.getPath(PROJECT_ROOT);
                        Path file         = args.getPath(FILE);
                        String source     = Files.readString(file);
                        int offset        = resolveOffset(args, source, file.getFileName().toString());
                        String target     = args.getString(TARGET_CLASS);
                        var project = new com.github.pfichtner.refactoring.project.MavenProject(projectRoot);
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
        Options opts = Options.of(FILE, LINE, COLUMN);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("promote_to_field", opts.toSchema())
                .description("Promote a local variable declaration to a private instance field of the enclosing class.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args    = opts.reader(request.arguments());
                        Path file   = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset  = resolveOffset(args, source, file.getFileName().toString());
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
        Options opts = Options.of(FILE, LINE, COLUMN);
        return SyncToolSpecification.builder()
                .tool(Tool.builder("convert_nested_to_top_level", opts.toSchema())
                .description("Convert a nested (member) type to a top-level type. Returns both the modified outer source and the new type's source.")
                .build())
                .callHandler((exchange, request) -> {
                    try {
                        var args   = opts.reader(request.arguments());
                        Path file  = args.getPath(FILE);
                        String source = Files.readString(file);
                        int offset = resolveOffset(args, source, file.getFileName().toString());
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
                .content(List.of(TextContent.builder(text).build()))
                .isError(false)
                .build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder()
                .content(List.of(TextContent.builder("Error: " + message).build()))
                .isError(true)
                .build();
    }
}
