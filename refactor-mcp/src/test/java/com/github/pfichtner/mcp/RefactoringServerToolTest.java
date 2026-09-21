package com.github.pfichtner.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        String text = call(RefactoringServer.extractMethod(), Map.of(
                "file", fixture("extract/simple/input/Greeter.java"),
                "start_line", 3, "start_column", 9,
                "end_line", 5, "end_column", 9,
                "method_name", "sayHi"));
        Approvals.verify(text);
    }

    @Test
    void tool_extract_variable_returns_preview() {
        String text = call(RefactoringServer.extractVariable(), Map.of(
                "file", fixture("extract-var/simple/input/Foo.java"),
                "start_line", 3, "start_column", 16,
                "end_line", 3, "end_column", 21,
                "var_name", "answer", "replace_all", true));
        Approvals.verify(text);
    }

    @Test
    void tool_extract_constant_returns_preview() {
        String text = call(RefactoringServer.extractConstant(), Map.of(
                "file", fixture("extract-const/simple/input/Foo.java"),
                "start_line", 3, "start_column", 16,
                "end_line", 3, "end_column", 23,
                "const_name", "PI", "replace_all", true));
        Approvals.verify(text);
    }

    @Test
    void tool_inline_variable_returns_preview() {
        String text = call(RefactoringServer.inlineVariable(), Map.of(
                "file", fixture("inline-var/multiple-uses/input/Foo.java"),
                "line", 3, "column", 16));
        Approvals.verify(text);
    }

    @Test
    void tool_inline_constant_returns_preview() {
        String text = call(RefactoringServer.inlineConstant(), Map.of(
                "file", fixture("inline-constant/int-constant/input/Foo.java"),
                "line", 5, "column", 21,
                "all_occurrences", true, "remove_declaration", true));
        Approvals.verify(text);
    }

    @Test
    void tool_extract_superclass_returns_preview() {
        String text = call(RefactoringServer.extractSuperclass(), Map.of(
                "file", fixture("extract-superclass/simple/input/Animal.java"),
                "superclass_name", "BaseAnimal"));
        Approvals.verify(text);
    }

    @Test
    void tool_extract_interface_returns_preview() {
        String text = call(RefactoringServer.extractInterface(), Map.of(
                "file", fixture("extract-interface/simple/input/Calculator.java"),
                "interface_name", "Arithmetic"));
        Approvals.verify(text);
    }

    @Test
    void tool_decompose_conditional_returns_preview() {
        String text = call(RefactoringServer.decomposeConditional(), Map.of(
                "file", fixture("decompose-conditional/basic/input/Foo.java"),
                "line", 6, "column", 9,
                "method_name", "isAdultPremium"));
        Approvals.verify(text);
    }

    @Test
    void tool_convert_anonymous_to_nested_returns_preview() {
        String text = call(RefactoringServer.convertAnonymousToNested(), Map.of(
                "file", fixture("convert-anonymous/simple/input/Outer.java"),
                "line", 4, "column", 22,
                "nested_class_name", "Worker"));
        Approvals.verify(text);
    }

    @Test
    void tool_convert_nested_to_top_level_returns_preview() {
        String text = call(RefactoringServer.convertNestedToTopLevel(), Map.of(
                "file", fixture("convert-nested/static-class/input/Outer.java"),
                "line", 15, "column", 25));
        Approvals.verify(text);
    }

    @Test
    void tool_promote_to_field_returns_preview() {
        String text = call(RefactoringServer.promoteToField(), Map.of(
                "file", fixture("promote-to-field/without-initializer/input/Counter.java"),
                "line", 4, "column", 15));
        Approvals.verify(text);
    }

    @Test
    void tool_introduce_indirection_returns_preview() {
        String text = call(RefactoringServer.introduceIndirection(), Map.of(
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
        String text = call(RefactoringServer.pullUpMethod(), Map.of(
                "project_root", project("pull-up-method"),
                "file", project("pull-up-method") + "/src/main/java/com/example/Dog.java",
                "method", "speak"));
        Approvals.verify(text);
    }

    @Test
    void tool_push_down_method_returns_preview() {
        String text = call(RefactoringServer.pushDownMethod(), Map.of(
                "project_root", project("push-down-method"),
                "file", project("push-down-method") + "/src/main/java/com/example/Shape.java",
                "method", "area"));
        Approvals.verify(text);
    }

    @Test
    void tool_move_method_returns_preview() {
        String text = call(RefactoringServer.moveMethod(), Map.of(
                "project_root", project("move-method"),
                "file", project("move-method") + "/src/main/java/com/example/Printer.java",
                "method", "format",
                "target_class", "Report"));
        Approvals.verify(text);
    }

    @Test
    void tool_remove_param_returns_preview() {
        String text = call(RefactoringServer.removeParam(), Map.of(
                "project_root", project("remove-param"),
                "file", project("remove-param") + "/src/main/java/com/example/Computation.java",
                "method", "add", "parameter", "c"));
        Approvals.verify(text);
    }

    @Test
    void tool_introduce_param_returns_preview() {
        String text = call(RefactoringServer.introduceParam(), Map.of(
                "project_root", project("introduce-param"),
                "file", project("introduce-param") + "/src/main/java/com/example/Greeter.java",
                "start_line", 5, "start_column", 40,
                "end_line", 5, "end_column", 47,
                "param_name", "whom"));
        Approvals.verify(text);
    }

    @Test
    void tool_inline_method_returns_preview() {
        String text = call(RefactoringServer.inlineMethod(), Map.of(
                "project_root", project("rename-method"),
                "file", project("rename-method") + "/src/main/java/com/example/App.java",
                "line", 6, "column", 27,
                "remove_declaration", true));
        Approvals.verify(text);
    }

    @Test
    void tool_pull_up_field_returns_preview() {
        String text = call(RefactoringServer.pullUpField(), Map.of(
                "project_root", project("pull-up-field"),
                "file", "src/main/java/com/example/Dog.java",
                "field", "breed"));
        Approvals.verify(text);
    }

    @Test
    void tool_push_down_field_returns_preview() {
        String text = call(RefactoringServer.pushDownField(), Map.of(
                "project_root", project("push-down-field"),
                "file", "src/main/java/com/example/Vehicle.java",
                "field", "maxSpeed"));
        Approvals.verify(text);
    }

    @Test
    void tool_introduce_static_factory_returns_preview() {
        String text = call(RefactoringServer.introduceStaticFactory(), Map.of(
                "project_root", project("static-factory"),
                "file", "src/main/java/com/example/Counter.java",
                "line", 7, "column", 5,
                "factory_method_name", "of",
                "make_constructor_private", true));
        Approvals.verify(text);
    }

    @Test
    void tool_introduce_parameter_object_returns_preview() {
        String text = call(RefactoringServer.introduceParameterObject(), Map.of(
                "project_root", project("introduce-param-object"),
                "file", "src/main/java/com/example/Printer.java",
                "method", "print",
                "param_names", List.of("x", "y"),
                "class_name", "Point"));
        Approvals.verify(text);
    }

    @Test
    void tool_change_method_signature_returns_preview() {
        String text = call(RefactoringServer.changeMethodSignature(), Map.of(
                "project_root", project("change-method-signature"),
                "file", "src/main/java/com/example/Converter.java",
                "method", "convert",
                "new_return_type", "Object",
                "param_order", List.of(1, 0)));
        Approvals.verify(text);
    }

    @Test
    void tool_encapsulate_field_returns_preview() {
        String text = call(RefactoringServer.encapsulateField(), Map.of(
                "project_root", project("encapsulate-field"),
                "file", "src/main/java/com/example/Person.java",
                "field", "name",
                "generate_setter", true));
        Approvals.verify(text);
    }

    @Test
    void tool_move_static_member_returns_preview() {
        String text = call(RefactoringServer.moveStaticMember(), Map.of(
                "project_root", project("move-static"),
                "file", project("move-static") + "/src/main/java/com/example/MathUtils.java",
                "line", 5, "column", 5,
                "target_class", "Helpers"));
        Approvals.verify(text);
    }

    @Test
    void tool_rename_package_returns_preview() {
        String text = call(RefactoringServer.renamePackage(), Map.of(
                "project_root", project("rename-package"),
                "old_package", "com.example.service",
                "new_package", "com.example.util"));
        Approvals.verify(text);
    }

    @Test
    void tool_move_class_returns_preview() {
        String text = call(RefactoringServer.moveClass(), Map.of(
                "project_root", project("move-class"),
                "file", project("move-class") + "/src/main/java/com/example/service/Calculator.java",
                "new_package", "com.example.util"));
        Approvals.verify(text);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String call(SyncToolSpecification spec, Map<String, Object> arguments) {
        CallToolResult result = spec.callHandler().apply(null, fakeRequest(arguments));
        assertThat(result.isError())
                .as("Tool call should not fail: " + textOf(result))
                .isFalse();
        return textOf(result);
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
        return CallToolRequest.builder().name("fake").arguments(arguments).build();
    }

    private static String textOf(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }
}