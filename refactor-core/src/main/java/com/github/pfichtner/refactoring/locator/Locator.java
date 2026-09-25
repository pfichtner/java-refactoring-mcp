package com.github.pfichtner.refactoring.locator;

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
 *
 * <p>Both a position locator and a name locator may be supplied together via
 * {@link Verified}. The resolver will confirm they point to the same identifier
 * and throw a descriptive mismatch error if they don't.
 */
public sealed interface Locator
        permits Locator.Position, Locator.LineOnly, Locator.MethodName, Locator.FieldName,
                Locator.TypeName, Locator.ParameterInMethod, Locator.VariableName,
                Locator.Verified {

    /** 1-based line and column — the classic position-based locator. */
    record Position(int line, int col) implements Locator {}

    /**
     * Line-only locator: resolves to the single named declaration at that line.
     * Fails with an error if the line contains zero or more than one named element —
     * in those cases use {@link Position} (line + column) or a name-based locator.
     */
    record LineOnly(int line) implements Locator {}

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
     * A local variable identified by name within a specific method or constructor.
     * {@code methodSpec} follows the same {@code "name"} or {@code "name(types)"}
     * syntax as {@link MethodName#nameSpec()} and is mandatory — local variables
     * with the same name can exist in multiple methods, so file-wide search is
     * not sensible.
     */
    record VariableName(String name, String methodSpec) implements Locator {}

    /**
     * Both a position locator and a name-based locator supplied together.
     * The resolver verifies they point to the same identifier; if they disagree
     * it throws a descriptive mismatch error rather than silently picking one.
     * When they agree the name-based offset (always the identifier start) is used.
     */
    record Verified(Locator positionLocator, Locator nameLocator) implements Locator {}

    /**
     * Builds a {@link Locator} from nullable inputs. {@code null} means "not specified".
     * Validates mutual-exclusion rules and throws {@link IllegalArgumentException} on conflict.
     * When both a position and a name-based locator are given a {@link Verified} locator
     * is returned; the resolver will cross-check them at resolution time.
     */
    static Locator from(Integer line, Integer column,
                        String method, String field, String type,
                        String parameter, String variable, String className) {
        boolean hasPosition = line != null || column != null;
        boolean hasName     = method != null || field != null || type != null
                              || parameter != null || variable != null;

        if (column != null && line == null)
            throw new IllegalArgumentException("column requires line to also be specified.");
        if (!hasPosition && !hasName)
            throw new IllegalArgumentException(
                    "Specify either a position (line, or line+column) or a name-based locator (method, field, type, variable, or parameter).");

        if (hasPosition && !hasName)
            return column == null ? new LineOnly(line) : new Position(line, column);

        Locator nameLoc = buildNameLocator(method, field, type, parameter, variable, className);

        if (!hasPosition)
            return nameLoc;

        // Both given — return Verified; resolver will cross-check at resolution time
        Locator posLoc = column == null ? new LineOnly(line) : new Position(line, column);
        return new Verified(posLoc, nameLoc);
    }

    private static Locator buildNameLocator(String method, String field, String type,
                                            String parameter, String variable, String className) {
        if (parameter != null) {
            if (method == null)
                throw new IllegalArgumentException("parameter requires 'method' to also be specified.");
            return new ParameterInMethod(method, parameter);
        }
        if (variable != null) {
            if (method == null)
                throw new IllegalArgumentException(
                        "variable requires 'method' to also be specified " +
                        "(local variables can share names across methods).");
            return new VariableName(variable, method);
        }
        int kindCount = (method != null ? 1 : 0) + (field != null ? 1 : 0) + (type != null ? 1 : 0);
        if (kindCount > 1)
            throw new IllegalArgumentException("Specify only one of: method, field, or type.");
        if (method != null) return new MethodName(method, className);
        if (field  != null) return new FieldName(field, className);
        if (type   != null) return new TypeName(type);
        throw new IllegalArgumentException("No valid locator provided.");
    }
}
