


























package com.github.pfichtner.refactoring.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Approval tests for the individual MCP tool handlers.
 *
 * Every tool is exercised through its {@code callHandler} (preview mode only,
 * nothing is written to disk) against the shared refactoring fixtures.
 */
class RefactoringServerToolTest {

    // -------------------------------------------------------------------------
    // Single-file tools
    // -------------------------------------------------------------------------

    @Test
    void tool_extract_method_returns_preview() {
        String text = call(ExtractMethodTool.extractMethod(), Map.of(
                "file", fixture("extract/simple/input/Greeter.java"),
                "start_line", 3, "start_column", 9,
                "end_line", 5, "end_column", 9,
                "method_name", "sayHi"));
        Approvals.verify(text);
    }

    @Test
    void tool_extract_variable_returns_preview() {
        String text = call(ExtractVariableTool.extractVariable(), Map.of(
                "file", fixture("extract-var/simple/input/Foo.java"),
                "start_line", 3, "start_column", 16,
                "end_line", 3, "end_column", 21,
                "var_name", "answer", "replace_all", true));
        Approvals.verify(text);
    }

    @Test
    void tool_extract_constant_returns_preview() {
        String text = call(ExtractConstantTool.extractConstant(), Map.of(
                "file", fixture("extract-const/simple/input/Foo.java"),
                "start_line", 3, "start_column", 16,
                "end_line", 3, "end_column", 23,
                "const_name", "PI", "replace_all", true));
        Approvals.verify(text);
    }

    @Test
    void tool_extract_constant_replace_all_returns_preview() {
        String text = call(ExtractConstantTool.extractConstant(), Map.of(
                "file", fixture("extract-const/replaces-all/input/Foo.java"),
                "start_line", 3, "start_column", 28,
                "end_line", 3, "end_column", 35,
                "const_name", "GREETING", "replace_all", true));
        Approvals.verify(text);
    }

    @Test
    void tool_extract_constant_without_replace_all_returns_preview() {
        String text = call(ExtractConstantTool.extractConstant(), Map.of(
                "file", fixture("extract-const/replaces-all/input/Foo.java"),
                "start_line", 3, "start_column", 28,
                "end_line", 3, "end_column", 35,
                "const_name", "GREETING", "replace_all", false));
        Approvals.verify(text);
    }

    @Test
    void tool_inline_variable_returns_preview() {
        String text = call(InlineVariableTool.inlineVariable(), Map.of(
                "file", fixture("inline-var/multiple-uses/input/Foo.java"),
                "line", 3, "column", 16));
        Approvals.verify(text);
    }

    @Test
    void tool_inline_variable_by_variable_and_method_name_returns_preview() {
        String text = call(InlineVariableTool.inlineVariable(), Map.of(
                "file", fixture("inline-var/multiple-uses/input/Foo.java"),
                "method", "run",
                "variable", "msg"));
        Approvals.verify(text);
    }

    @Test
    void tool_inline_constant_returns_preview() {
        String text = call(InlineConstantTool.inlineConstant(), Map.of(
                "file", fixture("inline-constant/int-constant/input/Foo.java"),
                "line", 5, "column", 21,
                "replace_all", true, "remove_declaration", true));
        Approvals.verify(text);
    }

    @Test
    void tool_extract_superclass_returns_preview() {
        String text = call(ExtractSuperclassTool.extractSuperclass(), Map.of(
                "file", fixture("extract-superclass/simple/input/Animal.java"),
                "superclass_name", "BaseAnimal"));
        Approvals.verify(text);
    }

    @Test
    void tool_extract_interface_returns_preview() {
        String text = call(ExtractInterfaceTool.extractInterface(), Map.of(
                "file", fixture("extract-interface/simple/input/Calculator.java"),
                "interface_name", "Arithmetic"));
        Approvals.verify(text);
    }

    @Test
    void tool_decompose_conditional_returns_preview() {
        String text = call(DecomposeConditionalTool.decomposeConditional(), Map.of(
                "file", fixture("decompose-conditional/basic/input/Foo.java"),
                "line", 6, "column", 9,
                "method_name", "isAdultPremium"));
        Approvals.verify(text);
    }

    @Test
    void tool_convert_anonymous_to_nested_returns_preview() {
        String text = call(ConvertAnonymousToNestedTool.convertAnonymousToNested(), Map.of(
                "file", fixture("convert-anonymous/simple/input/Outer.java"),
                "line", 4, "column", 22,
                "nested_class_name", "Worker"));
        Approvals.verify(text);
    }

    @Test
    void tool_convert_nested_to_top_level_returns_preview() {
        String text = call(ConvertNestedToTopLevelTool.convertNestedToTopLevel(), Map.of(
                "file", fixture("convert-nested/static-class/input/Outer.java"),
                "line", 15, "column", 25));
        Approvals.verify(text);
    }

    @Test
    void tool_promote_to_field_returns_preview() {
        String text = call(PromoteToFieldTool.promoteToField(), Map.of(
                "file", fixture("promote-to-field/without-initializer/input/Counter.java"),
                "line", 4, "column", 15));
        Approvals.verify(text);
    }

    @Test
    void tool_introduce_indirection_returns_preview() {
        String text = call(IntroduceIndirectionTool.introduceIndirection(), Map.of(
                "file", fixture("introduce-indirection/static-method/input/MathUtils.java"),
                "line", 3, "column", 26,
                "indirection_method_name", "computeSquare"));
        Approvals.verify(text);
    }

    // -------------------------------------------------------------------------
    // Project tools (name-based locators)
    // -------------------------------------------------------------------------

    @Test
    void tool_pull_up_method_returns_preview() {
        String text = call(PullUpMethodTool.pullUpMethod(), Map.of(
                "project_root", project("pull-up-method"),
                "file", project("pull-up-method") + "/src/main/java/com/example/Dog.java",
                "method", "speak"));
        Approvals.verify(text);
    }

    @Test
    void tool_push_down_method_returns_preview() {
        String text = call(PushDownMethodTool.pushDownMethod(), Map.of(
                "project_root", project("push-down-method"),
                "file", project("push-down-method") + "/src/main/java/com/example/Shape.java",
                "method", "area"));
        Approvals.verify(text);
    }

    @Test
    void tool_move_method_returns_preview() {
        String text = call(MoveMethodTool.moveMethod(), Map.of(
                "project_root", project("move-method"),
                "file", project("move-method") + "/src/main/java/com/example/Printer.java",
                "method", "format",
                "target_class", "com.example.Report"));
        Approvals.verify(text);
    }

    @Test
    void tool_remove_param_returns_preview() {
        String text = call(RemoveParamTool.removeParam(), Map.of(
                "project_root", project("remove-param"),
                "file", project("remove-param") + "/src/main/java/com/example/Computation.java",
                "method", "add", "parameter", "c"));
        Approvals.verify(text);
    }

    @Test
    void tool_introduce_param_returns_preview() {
        String text = call(IntroduceParamTool.introduceParam(), Map.of(
                "project_root", project("introduce-param"),
                "file", project("introduce-param") + "/src/main/java/com/example/Greeter.java",
                "start_line", 5, "start_column", 40,
                "end_line", 5, "end_column", 47,
                "param_name", "whom"));
        Approvals.verify(text);
    }

    @Test
    void tool_inline_method_returns_preview() {
        String text = call(InlineMethodTool.inlineMethod(), Map.of(
                "project_root", project("rename-method"),
                "file", project("rename-method") + "/src/main/java/com/example/App.java",
                "line", 6, "column", 27,
                "remove_declaration", true));
        Approvals.verify(text);
    }

    @Test
    void tool_pull_up_field_returns_preview() {
        String text = call(PullUpFieldTool.pullUpField(), Map.of(
                "project_root", project("pull-up-field"),
                "file", "src/main/java/com/example/Dog.java",
                "field", "breed"));
        Approvals.verify(text);
    }

    @Test
    void tool_push_down_field_returns_preview() {
        String text = call(PushDownFieldTool.pushDownField(), Map.of(
                "project_root", project("push-down-field"),
                "file", "src/main/java/com/example/Vehicle.java",
                "field", "maxSpeed"));
        Approvals.verify(text);
    }

    @Test
    void tool_introduce_static_factory_returns_preview() {
        String text = call(IntroduceStaticFactoryTool.introduceStaticFactory(), Map.of(
                "project_root", project("static-factory"),
                "file", "src/main/java/com/example/Counter.java",
                "line", 7, "column", 5,
                "factory_method_name", "of",
                "make_constructor_private", true));
        Approvals.verify(text);
    }

    @Test
    void tool_introduce_parameter_object_returns_preview() {
        String text = call(IntroduceParameterObjectTool.introduceParameterObject(), Map.of(
                "project_root", project("introduce-param-object"),
                "file", "src/main/java/com/example/Printer.java",
                "method", "print",
                "param_names", List.of("x", "y"),
                "class_name", "Point"));
        Approvals.verify(text);
    }

    @Test
    void tool_change_method_signature_returns_preview() {
        String text = call(ChangeMethodSignatureTool.changeMethodSignature(), Map.of(
                "project_root", project("change-method-signature"),
                "file", "src/main/java/com/example/Converter.java",
                "method", "convert",
                "new_return_type", "Object",
                "param_order", List.of(1, 0)));
        Approvals.verify(text);
    }

    @Test
    void tool_encapsulate_field_returns_preview() {
        String text = call(EncapsulateFieldTool.encapsulateField(), Map.of(
                "project_root", project("encapsulate-field"),
                "file", "src/main/java/com/example/Person.java",
                "field", "name",
                "generate_setter", true));
        Approvals.verify(text);
    }

    @Test
    void tool_move_static_member_returns_preview() {
        String text = call(MoveStaticMemberTool.moveStaticMember(), Map.of(
                "project_root", project("move-static"),
                "file", project("move-static") + "/src/main/java/com/example/MathUtils.java",
                "line", 5, "column", 5,
                "target_class", "com.example.Helpers"));
        Approvals.verify(text);
    }

    @Test
    void tool_rename_package_returns_preview() {
        String text = call(RenamePackageTool.renamePackage(), Map.of(
                "project_root", project("rename-package"),
                "old_package", "com.example.service",
                "new_package", "com.example.util"));
        Approvals.verify(text);
    }

    @Test
    void tool_move_class_returns_preview() {
        String text = call(MoveClassTool.moveClass(), Map.of(
                "project_root", project("move-class"),
                "file", project("move-class") + "/src/main/java/com/example/service/Calculator.java",
                "new_package", "com.example.util"));
        Approvals.verify(text, new Options().withScrubber(scrubProjectRoot()));
    }

    @Test
    void tool_convert_to_record_returns_preview() {
        String text = call(ConvertToRecordTool.convertToRecord(), Map.of(
                "project_root", project("convert-to-record"),
                "file", "src/main/java/com/example/Point.java"));
        Approvals.verify(text);
    }

    @Test
    void tool_convert_to_record_rejects_class_with_extends() {
        String text = callRaw(ConvertToRecordTool.convertToRecord(), Map.of(
                "project_root", project("convert-to-record"),
                "file", "src/main/java/com/example/Derived.java",
                "dryrun", true));
        Approvals.verify(text);
    }

    // -------------------------------------------------------------------------
    // Apply tests — every tool writes its changes by default (dryrun=false)
    // and returns a summary. Each test works on a fresh temp copy.
    // -------------------------------------------------------------------------

    @Test
    void tool_extract_method_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("extract/simple/input"), "Greeter.java");
        String text = callApply(ExtractMethodTool.extractMethod(), Map.of(
                "file", file,
                "start_line", 3, "start_column", 9,
                "end_line", 5, "end_column", 9,
                "method_name", "sayHi"));
        assertThat(text).startsWith("Changed 1 file(s)");
        assertThat(Path.of(file)).exists();
    }

    @Test
    void tool_extract_variable_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("extract-var/simple/input"), "Foo.java");
        String text = callApply(ExtractVariableTool.extractVariable(), Map.of(
                "file", file,
                "start_line", 3, "start_column", 16,
                "end_line", 3, "end_column", 21,
                "var_name", "answer", "replace_all", true));
        assertThat(text).startsWith("Changed 1 file(s)");
    }

    @Test
    void tool_extract_constant_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("extract-const/simple/input"), "Foo.java");
        String text = callApply(ExtractConstantTool.extractConstant(), Map.of(
                "file", file,
                "start_line", 3, "start_column", 16,
                "end_line", 3, "end_column", 23,
                "const_name", "PI", "replace_all", true));
        assertThat(text).startsWith("Changed 1 file(s)");
    }

    @Test
    void tool_inline_variable_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("inline-var/multiple-uses/input"), "Foo.java");
        String text = callApply(InlineVariableTool.inlineVariable(), Map.of(
                "file", file, "line", 3, "column", 16));
        assertThat(text).startsWith("Changed 1 file(s)");
    }

    @Test
    void tool_inline_constant_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("inline-constant/int-constant/input"), "Foo.java");
        String text = callApply(InlineConstantTool.inlineConstant(), Map.of(
                "file", file, "line", 5, "column", 21,
                "replace_all", true, "remove_declaration", true));
        assertThat(text).startsWith("Changed 1 file(s)");
    }

    @Test
    void tool_extract_superclass_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("extract-superclass/simple/input"), "Animal.java");
        String text = callApply(ExtractSuperclassTool.extractSuperclass(), Map.of(
                "file", file, "superclass_name", "BaseAnimal"));
        assertThat(text).startsWith("Changed 2 file(s)");
        assertThat(Path.of(file).getParent().resolve("BaseAnimal.java")).exists();
    }

    @Test
    void tool_extract_interface_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("extract-interface/simple/input"), "Calculator.java");
        String text = callApply(ExtractInterfaceTool.extractInterface(), Map.of(
                "file", file, "interface_name", "Arithmetic"));
        assertThat(text).startsWith("Changed 2 file(s)");
        assertThat(Path.of(file).getParent().resolve("Arithmetic.java")).exists();
    }

    @Test
    void tool_decompose_conditional_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("decompose-conditional/basic/input"), "Foo.java");
        String text = callApply(DecomposeConditionalTool.decomposeConditional(), Map.of(
                "file", file, "line", 6, "column", 9,
                "method_name", "isAdultPremium"));
        assertThat(text).startsWith("Changed 1 file(s)");
    }

    @Test
    void tool_convert_anonymous_to_nested_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("convert-anonymous/simple/input"), "Outer.java");
        String text = callApply(ConvertAnonymousToNestedTool.convertAnonymousToNested(), Map.of(
                "file", file, "line", 4, "column", 22,
                "nested_class_name", "Worker"));
        assertThat(text).startsWith("Changed 1 file(s)");
    }

    @Test
    void tool_convert_nested_to_top_level_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("convert-nested/static-class/input"), "Outer.java");
        callApply(ConvertNestedToTopLevelTool.convertNestedToTopLevel(), Map.of(
                "file", file, "line", 15, "column", 25));
        try (var javaFiles = Files.list(Path.of(file).getParent())) {
            assertThat(javaFiles.filter(p -> p.toString().endsWith(".java")).count()).isEqualTo(2);
        }
    }

    @Test
    void tool_promote_to_field_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("promote-to-field/without-initializer/input"), "Counter.java");
        String text = callApply(PromoteToFieldTool.promoteToField(), Map.of(
                "file", file, "line", 4, "column", 15));
        assertThat(text).startsWith("Changed 1 file(s)");
    }

    @Test
    void tool_introduce_indirection_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String file = copyToTemp(tmp, fixtureDir("introduce-indirection/static-method/input"), "MathUtils.java");
        String text = callApply(IntroduceIndirectionTool.introduceIndirection(), Map.of(
                "file", file, "line", 3, "column", 26,
                "indirection_method_name", "computeSquare"));
        assertThat(text).startsWith("Changed 1 file(s)");
    }

    @Test
    void tool_inline_method_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/rename-method"), ".");
        String file = root + "/src/main/java/com/example/App.java";
        String text = callApply(InlineMethodTool.inlineMethod(), Map.of(
                "project_root", root, "file", file,
                "line", 6, "column", 27, "remove_declaration", true));
        assertThat(text).startsWith("Changed ");
        assertThat(Path.of(file)).exists();
    }

    @Test
    void tool_pull_up_method_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/pull-up-method"), ".");
        String file = root + "/src/main/java/com/example/Dog.java";
        String text = callApply(PullUpMethodTool.pullUpMethod(), Map.of(
                "project_root", root, "file", file,
                "method", "speak"));
        assertThat(text).startsWith("Changed ");
        assertThat(Path.of(file)).exists();
    }

    @Test
    void tool_push_down_method_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/push-down-method"), ".");
        String file = root + "/src/main/java/com/example/Shape.java";
        String text = callApply(PushDownMethodTool.pushDownMethod(), Map.of(
                "project_root", root, "file", file,
                "method", "area"));
        assertThat(text).startsWith("Changed ");
    }

    @Test
    void tool_move_method_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/move-method"), ".");
        String file = root + "/src/main/java/com/example/Printer.java";
        String text = callApply(MoveMethodTool.moveMethod(), Map.of(
                "project_root", root, "file", file,
                "method", "format", "target_class", "com.example.Report"));
        assertThat(text).startsWith("Changed ");
        assertThat(Path.of(root, "src/main/java/com/example/Report.java")).exists();
    }

    @Test
    void tool_remove_param_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/remove-param"), ".");
        String file = root + "/src/main/java/com/example/Computation.java";
        String text = callApply(RemoveParamTool.removeParam(), Map.of(
                "project_root", root, "file", file,
                "method", "add", "parameter", "c"));
        assertThat(text).startsWith("Changed ");
    }

    @Test
    void tool_introduce_param_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/introduce-param"), ".");
        String file = root + "/src/main/java/com/example/Greeter.java";
        String text = callApply(IntroduceParamTool.introduceParam(), Map.of(
                "project_root", root, "file", file,
                "start_line", 5, "start_column", 40,
                "end_line", 5, "end_column", 47,
                "param_name", "whom"));
        assertThat(text).startsWith("Changed ");
    }

    @Test
    void tool_pull_up_field_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/pull-up-field"), ".");
        String text = callApply(PullUpFieldTool.pullUpField(), Map.of(
                "project_root", root,
                "file", root + "/src/main/java/com/example/Dog.java",
                "field", "breed"));
        assertThat(text).startsWith("Changed ");
    }

    @Test
    void tool_push_down_field_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/push-down-field"), ".");
        String text = callApply(PushDownFieldTool.pushDownField(), Map.of(
                "project_root", root,
                "file", root + "/src/main/java/com/example/Vehicle.java",
                "field", "maxSpeed"));
        assertThat(text).startsWith("Changed ");
    }

    @Test
    void tool_introduce_static_factory_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/static-factory"), ".");
        String text = callApply(IntroduceStaticFactoryTool.introduceStaticFactory(), Map.of(
                "project_root", root,
                "file", root + "/src/main/java/com/example/Counter.java",
                "line", 7, "column", 5,
                "factory_method_name", "of",
                "make_constructor_private", true));
        assertThat(text).startsWith("Changed ");
    }

    @Test
    void tool_introduce_parameter_object_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/introduce-param-object"), ".");
        String text = callApply(IntroduceParameterObjectTool.introduceParameterObject(), Map.of(
                "project_root", root,
                "file", root + "/src/main/java/com/example/Printer.java",
                "method", "print",
                "param_names", List.of("x", "y"),
                "class_name", "Point"));
        assertThat(text).startsWith("Changed ");
    }

    @Test
    void tool_change_method_signature_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/change-method-signature"), ".");
        String text = callApply(ChangeMethodSignatureTool.changeMethodSignature(), Map.of(
                "project_root", root,
                "file", root + "/src/main/java/com/example/Converter.java",
                "method", "convert",
                "new_return_type", "Object",
                "param_order", List.of(1, 0)));
        assertThat(text).startsWith("Changed ");
    }

    @Test
    void tool_encapsulate_field_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/encapsulate-field"), ".");
        String text = callApply(EncapsulateFieldTool.encapsulateField(), Map.of(
                "project_root", root,
                "file", root + "/src/main/java/com/example/Person.java",
                "field", "name",
                "generate_setter", true));
        assertThat(text).startsWith("Changed ");
    }

    @Test
    void tool_move_static_member_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/move-static"), ".");
        String file = root + "/src/main/java/com/example/MathUtils.java";
        String text = callApply(MoveStaticMemberTool.moveStaticMember(), Map.of(
                "project_root", root, "file", file,
                "line", 5, "column", 5,
                "target_class", "com.example.Helpers"));
        assertThat(text).startsWith("Changed ");
        assertThat(Path.of(root, "src/main/java/com/example/Helpers.java")).exists();
    }

    @Test
    void tool_rename_package_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/rename-package"), ".");
        String text = callApply(RenamePackageTool.renamePackage(), Map.of(
                "project_root", root,
                "old_package", "com.example.service",
                "new_package", "com.example.util"));
        assertThat(text).startsWith("Changed ");
        assertThat(Path.of(root, "src/main/java/com/example/util/Calculator.java")).exists();
        assertThat(Path.of(root, "src/main/java/com/example/service/Calculator.java")).doesNotExist();
    }

    @Test
    void tool_move_class_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/move-class"), ".");
        String text = callApply(MoveClassTool.moveClass(), Map.of(
                "project_root", root,
                "file", root + "/src/main/java/com/example/service/Calculator.java",
                "new_package", "com.example.util"));
        assertThat(text).startsWith("Changed ");
        assertThat(Path.of(root, "src/main/java/com/example/util/Calculator.java")).exists();
        assertThat(Path.of(root, "src/main/java/com/example/service/Calculator.java")).doesNotExist();
    }

    @Test
    void tool_convert_to_record_applies(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        String root = copyToTemp(tmp, fixtureDir("projects/convert-to-record"), ".");
        String text = callApply(ConvertToRecordTool.convertToRecord(), Map.of(
                "project_root", root,
                "file", root + "/src/main/java/com/example/Point.java"));
        assertThat(text).startsWith("Changed ");
        assertThat(Path.of(root, "src/main/java/com/example/Point.java")).exists();
    }

    private static Scrubber scrubProjectRoot() {
        String projectRoot = Path.of("").toAbsolutePath().normalize().toString();
        return input -> input.replace(projectRoot + File.separator, "{ROOT}/");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String call(SyncToolSpecification spec, Map<String, Object> arguments) {
        Map<String, Object> dryrunArgs = new HashMap<>(arguments);
        dryrunArgs.put("dryrun", true);
        CallToolResult result = spec.callHandler().apply(null, fakeRequest(dryrunArgs));
        assertThat(result.isError())
                .as("Tool call should not fail: " + textOf(result))
                .isFalse();
        return textOf(result);
    }

    private static String callRaw(SyncToolSpecification spec, Map<String, Object> arguments) {
        CallToolResult result = spec.callHandler().apply(null, fakeRequest(arguments));
        return (result.isError() ? "[ERROR]" : "[OK]") + "\n" + textOf(result);
    }

    private static String callApply(SyncToolSpecification spec, Map<String, Object> arguments) {
        CallToolResult result = spec.callHandler().apply(null, fakeRequest(arguments));
        assertThat(result.isError())
                .as("apply should not fail: " + textOf(result))
                .isFalse();
        return textOf(result);
    }

    /** Copies {@code fixtureDir(...) + "/" + rel} into {@code tmp} preserving the layout. */
    private static String copyToTemp(Path tmp, String resourceDir, String rel) throws Exception {
        Path src = Path.of(RefactoringServerToolTest.class.getClassLoader()
                .getResource(resourceDir).toURI());
        try (var files = Files.walk(src)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                Path dst = tmp.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(dst);
                else Files.copy(p, dst);
            }
        }
        return tmp.resolve(rel).toString();
    }

    private static String fixtureDir(String relative) {
        return "fixtures/" + relative;
    }

    private static String fixture(String relative) {
        try {
            return Path.of(RefactoringServerToolTest.class.getClassLoader()
                            .getResource("fixtures/" + relative).toURI())
                    .toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String project(String name) {
        try {
            return Path.of(RefactoringServerToolTest.class.getClassLoader()
                            .getResource("fixtures/projects/" + name + "/pom.xml").toURI())
                    .getParent().toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static CallToolRequest fakeRequest(Map<String, Object> arguments) {
        return CallToolRequest.builder("fake").arguments(arguments).build();
    }

    private static String textOf(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }
}