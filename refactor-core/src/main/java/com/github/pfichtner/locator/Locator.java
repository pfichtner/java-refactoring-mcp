package com.github.pfichtner.locator;

/**
 * Identifies a Java element to refactor — either by source position or by name.
 *
 * <p>Name-based locators are stable across edits and require no prior file read
 * to discover line/column numbers. They resolve to a char offset via
 * {@link LocatorResolver#resolve(Locator, String, String)}.
 *
 * <p>Method name syntax: {@code "add"} or {@code "add(int, int)"} when the
 * method is overloaded. An optional {@code className} scopes the search to a
 * specific type declaration when the file contains multiple types.
 */
public sealed interface Locator
        permits Locator.Position, Locator.MethodName, Locator.FieldName,
                Locator.TypeName, Locator.ParameterInMethod, Locator.VariableName {

    /** 1-based line and column — the classic position-based locator. */
    record Position(int line, int col) implements Locator {}

    /**
     * Method identified by name, with optional param-type list and enclosing class.
     * {@code nameSpec} is either {@code "add"} (unambiguous) or {@code "add(int,int)"}
     * (disambiguates overloads using simple type names).
     */
    record MethodName(String nameSpec, String className) implements Locator {
        public MethodName(String nameSpec) { this(nameSpec, null); }
    }

    /** Field identified by its identifier, with optional enclosing class name. */
    record FieldName(String name, String className) implements Locator {
        public FieldName(String name) { this(name, null); }
    }

    /** Type (class, interface, enum, annotation) identified by its simple name. */
    record TypeName(String name) implements Locator {}

    /**
     * A method parameter identified by its enclosing method and its own name.
     * {@code methodSpec} follows the same {@code "name"} or {@code "name(types)"}
     * syntax as {@link MethodName#nameSpec()}.
     * Used by remove-param.
     */
    record ParameterInMethod(String methodSpec, String paramName) implements Locator {}

    /**
     * A local variable or field identified by its simple name.
     * Used by inline-variable / inline-constant when the caller knows the name
     * but not the position.
     */
    record VariableName(String name) implements Locator {}
}
