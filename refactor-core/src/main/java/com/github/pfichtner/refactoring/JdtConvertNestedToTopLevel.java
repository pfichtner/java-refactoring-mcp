package com.github.pfichtner.refactoring;

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Headless Convert-Nested-Type-to-Top-Level refactoring using JDT ASTParser.
 *
 * <p>Moves a nested (member) type declaration out of its enclosing class and
 * into its own top-level compilation unit:
 *
 * <pre>{@code
 * // Before — Outer.java
 * public class Outer {
 *     private static class Helper {
 *         int compute(int x) { return x * 2; }
 *     }
 * }
 *
 * // After — Outer.java (modified):
 * public class Outer {
 * }
 *
 * // After — Helper.java (new file):
 * public class Helper {
 *     int compute(int x) { return x * 2; }
 * }
 * }</pre>
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No nested type declaration at the given offset</li>
 *   <li>The nested type is itself a top-level type (not inside another type)</li>
 * </ul>
 *
 * <p>Limitations:
 * <ul>
 *   <li>Only static nested classes and nested interfaces are supported.
 *       Inner (non-static) class conversion requires constructor injection of the enclosing
 *       instance, which is not implemented.</li>
 *   <li>Call sites that use {@code Outer.NestedName} are not updated automatically.</li>
 *   <li>The access modifier is set to {@code public}; original visibility modifiers are stripped.</li>
 * </ul>
 */
public class JdtConvertNestedToTopLevel {

    /**
     * Converts the nested type at {@code offset} to a top-level type.
     *
     * @param source   full source text of the compilation unit containing the nested type
     * @param unitName file name for error messages (e.g. {@code "Outer.java"})
     * @param offset   character offset anywhere inside the nested type declaration
     * @return a {@link Result} containing the modified outer source and the new type's source
     * @throws IllegalArgumentException if preconditions are not met
     */
    public static Result convert(String source, String unitName, int offset) {
        CompilationUnit cu = parse(source, unitName);

        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at offset " + offset + ".");
        }

        TypeDeclaration nested = findNestedType(node);
        if (nested == null) {
            throw new IllegalArgumentException(
                    "No nested type declaration found at offset " + offset + ".");
        }

        if (!(nested.getParent() instanceof TypeDeclaration enclosingType)) {
            throw new IllegalArgumentException(
                    "The type at offset " + offset + " is not a member of another type.");
        }

        // Check for inner class (non-static) — not supported
        boolean isStatic = false;
        for (Object mod : nested.modifiers()) {
            if (mod instanceof Modifier m && m.getKeyword() == Modifier.ModifierKeyword.STATIC_KEYWORD) {
                isStatic = true;
                break;
            }
        }
        if (!nested.isInterface() && !isStatic) {
            throw new IllegalArgumentException(
                    "Inner (non-static) class '" + nested.getName().getIdentifier()
                    + "' cannot be converted to top-level without injecting the enclosing "
                    + "instance as a constructor parameter. Make the class static first.");
        }

        // Collect package declaration from outer source
        String packageDecl = extractPackageDecl(cu);

        // Extract the nested type source block (with its indentation stripped)
        int nestedStart = nested.getStartPosition();
        int nestedEnd   = nestedStart + nested.getLength();
        String rawNested = source.substring(nestedStart, nestedEnd);

        // Build the new top-level file source
        String newFileSource = buildTopLevelSource(packageDecl, rawNested);

        // Remove the nested type from the outer source
        String outerSource = removeNestedFromOuter(source, nested);

        return new Result(outerSource, newFileSource, nested.getName().getIdentifier());
    }

    /**
     * Result of a nested-to-top-level conversion.
     *
     * @param outerSource    modified source of the original file (nested type removed)
     * @param newTypeSource  source for the new top-level file
     * @param newTypeName    simple name of the extracted type (use as {@code <Name>.java})
     */
    public record Result(String outerSource, String newTypeSource, String newTypeName) {}

    // -------------------------------------------------------------------------
    // Text generation
    // -------------------------------------------------------------------------

    private static String buildTopLevelSource(String packageDecl, String rawNested) {
        String[] lines = rawNested.split("\n", -1);

        // Find minimum indentation of non-blank lines excluding line 0 (the class header)
        int minIndent = Integer.MAX_VALUE;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                int sp = 0;
                while (sp < lines[i].length() && lines[i].charAt(sp) == ' ') sp++;
                minIndent = Math.min(minIndent, sp);
            }
        }
        if (minIndent == Integer.MAX_VALUE) minIndent = 0;

        // Rebuild with stripped indentation
        StringBuilder body = new StringBuilder();
        // Line 0: strip modifiers and prepend "public "
        String header = stripInvalidTopLevelModifiers(lines[0].stripLeading());
        body.append("public ").append(header).append("\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                body.append("\n");
            } else {
                String stripped = line.length() >= minIndent ? line.substring(minIndent) : line.stripLeading();
                body.append(stripped).append("\n");
            }
        }

        // Remove trailing blank lines but keep final newline
        String bodyStr = body.toString().stripTrailing() + "\n";

        StringBuilder sb = new StringBuilder();
        if (!packageDecl.isEmpty()) {
            sb.append(packageDecl).append("\n\n");
        }
        sb.append(bodyStr);
        return sb.toString();
    }

    private static String stripInvalidTopLevelModifiers(String raw) {
        // Remove leading access/static modifiers before "class" or "interface"
        String result = raw;
        for (String mod : List.of("private ", "protected ", "static ", "public ")) {
            while (result.startsWith(mod)) {
                result = result.substring(mod.length());
            }
        }
        return result;
    }

    private static String removeNestedFromOuter(String source, TypeDeclaration nested) {
        int start = nested.getStartPosition();
        int end   = start + nested.getLength();

        // Walk start back to beginning of its line
        int lineStart = start;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;

        // Walk end forward to consume the trailing newline
        while (end < source.length() && source.charAt(end) != '\n') end++;
        if (end < source.length()) end++;

        // Consume one preceding blank line if present
        if (lineStart >= 2
                && source.charAt(lineStart - 1) == '\n'
                && source.charAt(lineStart - 2) == '\n') {
            lineStart--;
        }

        return source.substring(0, lineStart) + source.substring(end);
    }

    private static String extractPackageDecl(CompilationUnit cu) {
        PackageDeclaration pkg = cu.getPackage();
        if (pkg == null) return "";
        return "package " + pkg.getName().getFullyQualifiedName() + ";";
    }

    // -------------------------------------------------------------------------
    // AST navigation
    // -------------------------------------------------------------------------

    private static TypeDeclaration findNestedType(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof TypeDeclaration td
                    && td.getParent() instanceof TypeDeclaration) {
                return td;
            }
            current = current.getParent();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    private static CompilationUnit parse(String source, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(false);
        return (CompilationUnit) parser.createAST(null);
    }
}
