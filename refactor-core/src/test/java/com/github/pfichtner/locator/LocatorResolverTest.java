package com.github.pfichtner.locator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LocatorResolver}.
 *
 * Each test calls resolve() directly on a short inline source string,
 * then verifies the returned offset points at the expected identifier.
 */
class LocatorResolverTest {

    // -------------------------------------------------------------------------
    // Position locator (existing behaviour, just delegated through the interface)
    // -------------------------------------------------------------------------

    @Test
    void position_resolves_to_correct_offset() {
        String source = "package foo;\npublic class Bar {}";
        // "Bar" is at line 2, col 14 → offset = 13 ("package foo;\n") + 13 = 26
        int offset = resolve(new Locator.Position(2, 14), source);
        assertThat(tokenAt(source, offset, 3)).isEqualTo("Bar");
    }

    // -------------------------------------------------------------------------
    // MethodName — unambiguous
    // -------------------------------------------------------------------------

    @Test
    void method_name_resolves_unique_method() {
        String source = """
                public class Calc {
                    public int add(int a, int b) { return a + b; }
                }
                """;
        int offset = resolve(new Locator.MethodName("add"), source);
        assertThat(tokenAt(source, offset, 3)).isEqualTo("add");
    }

    @Test
    void method_name_with_param_types_resolves_correct_overload() {
        String source = """
                public class Calc {
                    public int add(int a) { return a; }
                    public int add(int a, int b) { return a + b; }
                }
                """;
        // Resolve the 2-param overload
        int offset = resolve(new Locator.MethodName("add(int, int)"), source);
        // Verify the resolved "add" is from the second declaration (character index > first one)
        int firstAdd = source.indexOf("add");
        int secondAdd = source.indexOf("add", firstAdd + 1);
        assertThat(offset).isEqualTo(secondAdd);
    }

    @Test
    void method_name_with_no_params_resolves_zero_param_overload() {
        String source = """
                public class Calc {
                    public void reset() {}
                    public int add(int a, int b) { return a + b; }
                }
                """;
        int offset = resolve(new Locator.MethodName("reset()"), source);
        assertThat(tokenAt(source, offset, 5)).isEqualTo("reset");
    }

    @Test
    void method_name_scoped_to_class() {
        String source = """
                public class Outer {
                    public void foo() {}
                    class Inner {
                        public void foo() {}
                    }
                }
                """;
        int offset = resolve(new Locator.MethodName("foo", "Inner"), source);
        // The Inner.foo "foo" appears after Outer.foo "foo"
        int firstFoo = source.indexOf("foo");
        int innerFoo = source.indexOf("foo", firstFoo + 1);
        assertThat(offset).isEqualTo(innerFoo);
    }

    @Test
    void ambiguous_method_without_param_types_throws() {
        String source = """
                public class Calc {
                    public int add(int a) { return a; }
                    public int add(int a, int b) { return a + b; }
                }
                """;
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resolve(new Locator.MethodName("add"), source)).actual();
        assertThat(ex.getMessage()).contains("Ambiguous");
        assertThat(ex.getMessage()).contains("add");
    }

    @Test
    void unknown_method_throws() {
        String source = "public class Foo { public void bar() {} }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resolve(new Locator.MethodName("baz"), source)).actual();
        assertThat(ex.getMessage()).contains("baz");
    }

    // -------------------------------------------------------------------------
    // FieldName
    // -------------------------------------------------------------------------

    @Test
    void field_name_resolves_unique_field() {
        String source = """
                public class Person {
                    public String name;
                    public int age;
                }
                """;
        int offset = resolve(new Locator.FieldName("name"), source);
        assertThat(tokenAt(source, offset, 4)).isEqualTo("name");
    }

    @Test
    void field_name_scoped_to_class() {
        String source = """
                public class A {
                    int count;
                    class B { int count; }
                }
                """;
        int offset = resolve(new Locator.FieldName("count", "B"), source);
        int firstCount = source.indexOf("count");
        int secondCount = source.indexOf("count", firstCount + 1);
        assertThat(offset).isEqualTo(secondCount);
    }

    @Test
    void unknown_field_throws() {
        String source = "public class Foo { int bar; }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resolve(new Locator.FieldName("baz"), source)).actual();
        assertThat(ex.getMessage()).contains("baz");
    }

    // -------------------------------------------------------------------------
    // TypeName
    // -------------------------------------------------------------------------

    @Test
    void type_name_resolves_class() {
        String source = "package p; public class OrderService {}";
        int offset = resolve(new Locator.TypeName("OrderService"), source);
        assertThat(tokenAt(source, offset, 12)).isEqualTo("OrderService");
    }

    @Test
    void unknown_type_throws() {
        String source = "public class Foo {}";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resolve(new Locator.TypeName("Bar"), source)).actual();
        assertThat(ex.getMessage()).contains("Bar");
    }

    // -------------------------------------------------------------------------
    // ParameterInMethod
    // -------------------------------------------------------------------------

    @Test
    void parameter_in_method_resolves_param() {
        String source = """
                public class Svc {
                    public void setName(String unused, int id) {}
                }
                """;
        int offset = resolve(new Locator.ParameterInMethod("setName", "unused"), source);
        assertThat(tokenAt(source, offset, 6)).isEqualTo("unused");
    }

    @Test
    void parameter_in_method_with_overload_spec_resolves_correct_overload() {
        String source = """
                public class Svc {
                    public void process(String input) {}
                    public void process(int count, String name) {}
                }
                """;
        int offset = resolve(new Locator.ParameterInMethod("process(int, String)", "name"), source);
        assertThat(tokenAt(source, offset, 4)).isEqualTo("name");
    }

    @Test
    void unknown_parameter_throws() {
        String source = "public class Svc { public void foo(int x) {} }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> resolve(new Locator.ParameterInMethod("foo", "y"), source)).actual();
        assertThat(ex.getMessage()).contains("y");
    }

    // -------------------------------------------------------------------------
    // VariableName
    // -------------------------------------------------------------------------

    @Test
    void variable_name_resolves_local_variable() {
        String source = """
                public class Calc {
                    public int compute(int a) {
                        int result = a * 2;
                        return result;
                    }
                }
                """;
        int offset = resolve(new Locator.VariableName("result", "compute"), source);
        assertThat(tokenAt(source, offset, 6)).isEqualTo("result");
    }

    @Test
    void variable_name_scoped_to_method_ignores_same_name_in_other_method() {
        String source = """
                public class Calc {
                    public int compute(int a) {
                        int result = a * 2;
                        return result;
                    }
                    public int other(int b) {
                        int result = b + 1;
                        return result;
                    }
                }
                """;
        // resolve "result" in compute — offset must point before "other" method starts
        int offset = resolve(new Locator.VariableName("result", "compute"), source);
        assertThat(tokenAt(source, offset, 6)).isEqualTo("result");
        assertThat(offset).isLessThan(source.indexOf("other"));
    }

    @Test
    void variable_name_with_method_overload_requires_param_types_when_ambiguous() {
        String source = """
                public class Svc {
                    public void process(int count) {
                        int result = count * 2;
                    }
                    public void process(String label) {
                        int result = label.length();
                    }
                }
                """;
        // "process" is ambiguous without param types → resolver must throw
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolve(new Locator.VariableName("result", "process"), source)).actual();
        assertThat(ex.getMessage()).containsIgnoringCase("ambiguous");

        // With param types, resolves unambiguously to the int overload
        int offset = resolve(new Locator.VariableName("result", "process(int)"), source);
        assertThat(tokenAt(source, offset, 6)).isEqualTo("result");
        // Must be inside process(int count), i.e. before process(String label)
        int secondProcessStart = source.indexOf("process", source.indexOf("process") + 1);
        assertThat(offset).isLessThan(secondProcessStart);
    }

    @Test
    void variable_does_not_match_field() {
        String source = "public class Foo { int val; public void m() { int x = 1; } }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolve(new Locator.VariableName("val", "m"), source)).actual();
        assertThat(ex.getMessage()).contains("val");
    }

    @Test
    void unknown_variable_throws() {
        String source = "public class Foo { public void m() { int x = 1; } }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolve(new Locator.VariableName("missing", "m"), source)).actual();
        assertThat(ex.getMessage()).contains("missing");
    }

    @Test
    void variable_name_unknown_method_throws() {
        String source = "public class Foo { public void m() { int x = 1; } }";
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolve(new Locator.VariableName("x", "noSuchMethod"), source)).actual();
        assertThat(ex.getMessage()).contains("noSuchMethod");
    }

    @Test
    void variable_name_ambiguous_when_same_name_in_multiple_loops_throws() {
        String source = """
                public class Loops {
                    public void run() {
                        for (int i = 0; i < 10; i++) {}
                        for (int i = 0; i < 20; i++) {}
                    }
                }
                """;
        // Two "i" declarations inside the same method → ambiguous even with method scope
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolve(new Locator.VariableName("i", "run"), source)).actual();
        assertThat(ex.getMessage()).containsIgnoringCase("ambiguous");
        assertThat(ex.getMessage()).contains("i");
    }

    // -------------------------------------------------------------------------
    // LineOnly
    // -------------------------------------------------------------------------

    @Test
    void line_only_resolves_unique_variable() {
        String source = """
                public class Calc {
                    public int compute(int a) {
                        int result = a * 2;
                        return result;
                    }
                }
                """;
        // "result" is the only named declaration on its line
        int offset = resolve(new Locator.LineOnly(3), source);
        assertThat(tokenAt(source, offset, 6)).isEqualTo("result");
    }

    @Test
    void line_only_resolves_unique_field() {
        String source = """
                public class Person {
                    private String name;
                    private int age;
                }
                """;
        int offset = resolve(new Locator.LineOnly(2), source);
        assertThat(tokenAt(source, offset, 4)).isEqualTo("name");
    }

    @Test
    void line_only_ambiguous_multiple_declarations_throws() {
        String source = """
                public class Calc {
                    public int compute(int a) {
                        int i = 0, j = 0;
                        return i + j;
                    }
                }
                """;
        // "i" and "j" are both declared on line 3
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolve(new Locator.LineOnly(3), source)).actual();
        assertThat(ex.getMessage()).containsIgnoringCase("ambiguous");
    }

    @Test
    void line_only_no_element_throws() {
        String source = """
                public class Calc {
                    // just a comment
                    public int compute(int a) { return a; }
                }
                """;
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolve(new Locator.LineOnly(2), source)).actual();
        assertThat(ex.getMessage()).containsIgnoringCase("no named element");
    }

    @Test
    void line_only_ambiguous_method_with_param_throws() {
        String source = """
                public class Svc {
                    public void process(int count) {}
                }
                """;
        // line 2 has both "process" (method) and "count" (parameter)
        var ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> resolve(new Locator.LineOnly(2), source)).actual();
        assertThat(ex.getMessage()).containsIgnoringCase("ambiguous");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int resolve(Locator locator, String source) {
        return LocatorResolver.resolve(locator, source, "Test.java");
    }

    private static String tokenAt(String source, int offset, int length) {
        return source.substring(offset, offset + length);
    }
}
