package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.locator.Locator;
import com.github.pfichtner.refactoring.locator.LocatorResolver;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;

/**
 * Picocli mixin that provides a name-based alternative to {@code --line}/{@code --column}.
 *
 * <p>Either the position group ({@code --line} + {@code --column}) or exactly one
 * name-based option ({@code --method}, {@code --field}, {@code --type}, {@code --variable})
 * must be supplied. For {@code remove-param}, combine {@code --method} with {@code --parameter}.
 *
 * <p>After parsing, call {@link #resolveOffset(String, String)} to get the char offset.
 */
public class LocatorOptions {

    @ArgGroup(exclusive = false, heading = "Position locator (--column optional when line has one element):%n")
    PositionGroup position;

    @ArgGroup(exclusive = false, heading = "Name-based locator (alternative to --line/--column):%n")
    NameGroup name;

    static class PositionGroup {
        @Option(names = {"--line", "-l"}, required = true,
                description = "1-based line number of the target element.")
        int line;

        @Option(names = {"--column", "-c"},
                description = "1-based column number of the target element. " +
                              "Omit if the line contains exactly one named declaration.")
        Integer column;
    }

    static class NameGroup {
        @Option(names = "--method",
                description = "Method name, e.g. \"add\" or \"add(int,int)\" for overloads.")
        String method;

        @Option(names = "--field",
                description = "Field name, e.g. \"amount\".")
        String field;

        @Option(names = "--type",
                description = "Type name (class/interface/enum), e.g. \"OrderService\".")
        String type;

        @Option(names = "--variable",
                description = "Local variable name, e.g. \"result\".")
        String variable;

        @Option(names = "--parameter",
                description = "Parameter name for remove-param (combine with --method).")
        String parameter;

        @Option(names = "--class",
                description = "Optional: restrict name-based search to this class (when file has multiple types).")
        String enclosingClass;
    }

    /**
     * Returns the 0-based char offset of the identified element within {@code source}.
     *
     * @param source   full source text of the target file
     * @param unitName simple file name used in error messages
     * @throws IllegalArgumentException if no locator was supplied or inputs are invalid
     */
    public int resolveOffset(String source, String unitName) {
        return LocatorResolver.resolve(buildLocator(), source, unitName);
    }

    private Locator buildLocator() {
        boolean hasPosition = position != null;
        boolean hasName     = name != null;

        if (!hasPosition && !hasName) {
            throw new IllegalArgumentException(
                    "Specify either (--line + --column) or a name-based locator " +
                    "(--method, --field, --type, --variable, or --parameter with --method).");
        }
        if (hasPosition && hasName) {
            throw new IllegalArgumentException(
                    "Specify either (--line + --column) OR a name-based locator, not both.");
        }

        if (hasPosition) {
            if (position.column == null)
                return new Locator.LineOnly(position.line);
            return new Locator.Position(position.line, position.column);
        }

        if (name.parameter != null) {
            if (name.method == null) {
                throw new IllegalArgumentException("--parameter requires --method to also be specified.");
            }
            return new Locator.ParameterInMethod(name.method, name.parameter);
        }

        if (name.variable != null) {
            if (name.method == null) {
                throw new IllegalArgumentException(
                        "--variable requires --method to also be specified " +
                        "(local variables can share names across methods).");
            }
            return new Locator.VariableName(name.variable, name.method);
        }

        int kindCount = (name.method != null ? 1 : 0) + (name.field != null ? 1 : 0)
                + (name.type != null ? 1 : 0);
        if (kindCount > 1) {
            throw new IllegalArgumentException("Specify only one of: --method, --field, or --type.");
        }

        if (name.method   != null) return new Locator.MethodName(name.method, name.enclosingClass);
        if (name.field    != null) return new Locator.FieldName(name.field, name.enclosingClass);
        if (name.type     != null) return new Locator.TypeName(name.type);

        throw new IllegalArgumentException("No valid locator provided.");
    }

}
