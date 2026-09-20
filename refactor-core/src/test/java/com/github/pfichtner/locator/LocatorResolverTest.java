package com.github.pfichtner.locator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals("Bar", tokenAt(source, offset, 3));
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
        assertEquals("add", tokenAt(source, offset, 3));
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
        assertEquals(secondAdd, offset);
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
        assertEquals("reset", tokenAt(source, offset, 5));
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
        assertEquals(innerFoo, offset);
    }

    @Test
    void ambiguous_method_without_param_types_throws() {
        String source = """
                public class Calc {
                    public int add(int a) { return a; }
                    public int add(int a, int b) { return a + b; }
                }
                """;
        var ex = assertThrows(IllegalArgumentException.class,
                () -> resolve(new Locator.MethodName("add"), source));
        assertTrue(ex.getMessage().contains("Ambiguous"), ex.getMessage());
        assertTrue(ex.getMessage().contains("add"), ex.getMessage());
    }

    @Test
    void unknown_method_throws() {
        String source = "public class Foo { public void bar() {} }";
        var ex = assertThrows(IllegalArgumentException.class,
                () -> resolve(new Locator.MethodName("baz"), source));
        assertTrue(ex.getMessage().contains("baz"), ex.getMessage());
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
        assertEquals("name", tokenAt(source, offset, 4));
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
        assertEquals(secondCount, offset);
    }

    @Test
    void unknown_field_throws() {
        String source = "public class Foo { int bar; }";
        var ex = assertThrows(IllegalArgumentException.class,
                () -> resolve(new Locator.FieldName("baz"), source));
        assertTrue(ex.getMessage().contains("baz"), ex.getMessage());
    }

    // -------------------------------------------------------------------------
    // TypeName
    // -------------------------------------------------------------------------

    @Test
    void type_name_resolves_class() {
        String source = "package p; public class OrderService {}";
        int offset = resolve(new Locator.TypeName("OrderService"), source);
        assertEquals("OrderService", tokenAt(source, offset, 12));
    }

    @Test
    void unknown_type_throws() {
        String source = "public class Foo {}";
        var ex = assertThrows(IllegalArgumentException.class,
                () -> resolve(new Locator.TypeName("Bar"), source));
        assertTrue(ex.getMessage().contains("Bar"), ex.getMessage());
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
        assertEquals("unused", tokenAt(source, offset, 6));
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
        assertEquals("name", tokenAt(source, offset, 4));
    }

    @Test
    void unknown_parameter_throws() {
        String source = "public class Svc { public void foo(int x) {} }";
        var ex = assertThrows(IllegalArgumentException.class,
                () -> resolve(new Locator.ParameterInMethod("foo", "y"), source));
        assertTrue(ex.getMessage().contains("y"), ex.getMessage());
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
        int offset = resolve(new Locator.VariableName("result"), source);
        assertEquals("result", tokenAt(source, offset, 6));
    }

    @Test
    void variable_does_not_match_field() {
        String source = "public class Foo { int val; public void m() { int x = 1; } }";
        // "val" is a field, should not match VariableName
        var ex = assertThrows(IllegalArgumentException.class,
                () -> resolve(new Locator.VariableName("val"), source));
        assertTrue(ex.getMessage().contains("val"), ex.getMessage());
    }

    @Test
    void unknown_variable_throws() {
        String source = "public class Foo { public void m() { int x = 1; } }";
        var ex = assertThrows(IllegalArgumentException.class,
                () -> resolve(new Locator.VariableName("missing"), source));
        assertTrue(ex.getMessage().contains("missing"), ex.getMessage());
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
