package com.github.pfichtner;

import org.eclipse.jdt.core.dom.*;

import java.util.List;

/**
 * Headless Promote-Local-Variable-to-Field refactoring using JDT ASTParser.
 *
 * <p>Converts a local variable declaration inside a method into a private instance field
 * of the enclosing class:
 *
 * <pre>{@code
 * // Before
 * public class Calc {
 *     int compute(int x) {
 *         int result = x * 2;
 *         return result;
 *     }
 * }
 *
 * // After: promote(source, "Calc.java", offset_of_result)
 * public class Calc {
 *     private int result;
 *
 *     int compute(int x) {
 *         result = x * 2;
 *         return result;
 *     }
 * }
 * }</pre>
 *
 * <p>If the declaration has no initializer (e.g. {@code int count;}), the declaration
 * statement is simply removed and a field is added with no initializer.
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No local variable declaration at the given offset</li>
 *   <li>The variable is not inside a method (e.g. already a field)</li>
 *   <li>The enclosing class already declares a field with the same name</li>
 *   <li>Multi-fragment declarations (e.g. {@code int x, y;}) are not supported</li>
 * </ul>
 */
public class JdtPromoteToField {

    /**
     * Promotes the local variable declaration at {@code offset} to a private field.
     *
     * @param source   full source text
     * @param unitName file name for error messages (e.g. {@code "Calc.java"})
     * @param offset   character offset inside the local variable declaration
     * @return rewritten source with the variable promoted to a field
     * @throws IllegalArgumentException if preconditions are not met
     */
    public static String promote(String source, String unitName, int offset) {
        CompilationUnit cu = parse(source, unitName);

        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at offset " + offset + ".");
        }

        // Find the VariableDeclarationStatement enclosing the offset
        VariableDeclarationStatement vds = findVariableDeclarationStatement(node);
        if (vds == null) {
            throw new IllegalArgumentException(
                    "No local variable declaration found at offset " + offset + ". "
                    + "Place cursor on the variable name or type.");
        }

        @SuppressWarnings("unchecked")
        List<VariableDeclarationFragment> fragments = vds.fragments();
        if (fragments.size() > 1) {
            throw new IllegalArgumentException(
                    "Multi-fragment declarations (e.g. 'int x, y;') are not supported. "
                    + "Split the declaration first.");
        }

        VariableDeclarationFragment fragment = fragments.get(0);
        String varName = fragment.getName().getIdentifier();

        // Find the enclosing type
        TypeDeclaration enclosingType = findEnclosingType(vds);
        if (enclosingType == null) {
            throw new IllegalArgumentException(
                    "Variable '" + varName + "' is not inside a class body.");
        }

        // Check for duplicate field name
        for (Object bd : enclosingType.bodyDeclarations()) {
            if (bd instanceof FieldDeclaration fd) {
                for (Object frag : fd.fragments()) {
                    if (frag instanceof VariableDeclarationFragment f
                            && f.getName().getIdentifier().equals(varName)) {
                        throw new IllegalArgumentException(
                                "Field '" + varName + "' already exists in '"
                                + enclosingType.getName().getIdentifier() + "'.");
                    }
                }
            }
        }

        // Determine type name
        String typeName = vds.getType().toString();

        // Build the field declaration text
        String fieldDecl = "private " + typeName + " " + varName + ";";

        // Build the replacement for the local variable statement:
        // If there's an initializer: keep it as assignment statement
        // If no initializer: remove the declaration statement entirely
        Expression initializer = fragment.getInitializer();
        String assignmentReplacement = null;
        if (initializer != null) {
            String initText = source.substring(
                    initializer.getStartPosition(),
                    initializer.getStartPosition() + initializer.getLength());
            assignmentReplacement = varName + " = " + initText + ";";
        }

        // Find insertion point for the field: before the first method/field or just inside opening brace
        String memberIndent = detectIndent(source, enclosingType);

        return insertFieldAndReplaceLocal(
                source, enclosingType, vds, fieldDecl, assignmentReplacement, memberIndent);
    }

    // -------------------------------------------------------------------------
    // Core transformation
    // -------------------------------------------------------------------------

    private static String insertFieldAndReplaceLocal(
            String source, TypeDeclaration enclosingType,
            VariableDeclarationStatement vds,
            String fieldDecl, String assignmentReplacement,
            String memberIndent) {

        // Find where the local variable declaration starts (including leading indent)
        int vdsStart = vds.getStartPosition();
        int vdsEnd   = vdsStart + vds.getLength();

        // Walk start back to beginning of its line (to include indentation)
        int lineStart = vdsStart;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;

        // Walk end forward past trailing whitespace to consume the newline
        int lineEnd = vdsEnd;
        while (lineEnd < source.length() && source.charAt(lineEnd) != '\n') lineEnd++;
        if (lineEnd < source.length()) lineEnd++;

        // Build the replacement for the local declaration line
        String replacement;
        if (assignmentReplacement != null) {
            String indent = source.substring(lineStart, vdsStart);
            replacement = indent + assignmentReplacement + "\n";
        } else {
            // No initializer: remove the entire declaration line.
            // Also consume a preceding blank line if present to avoid double blank lines.
            if (lineStart >= 2
                    && source.charAt(lineStart - 1) == '\n'
                    && source.charAt(lineStart - 2) == '\n') {
                lineStart--;
            }
            replacement = "";
        }

        // Replace the local variable line
        StringBuilder sb = new StringBuilder(source);
        sb.replace(lineStart, lineEnd, replacement);
        String intermediate = sb.toString();

        // Now insert the field declaration before the first method, after any existing fields
        CompilationUnit cu2 = parse(intermediate, "tmp");
        TypeDeclaration type2 = findPrimaryType(cu2);

        String newField = memberIndent + fieldDecl + "\n";

        // Find the last existing field to insert after it, or the opening brace
        FieldDeclaration lastField = null;
        for (Object bd : type2.bodyDeclarations()) {
            if (bd instanceof FieldDeclaration fd) lastField = fd;
        }

        if (lastField != null) {
            int insertAt = lastField.getStartPosition() + lastField.getLength();
            String before = intermediate.substring(0, insertAt).stripTrailing();
            String after  = intermediate.substring(insertAt);
            return before + "\n" + newField + after;
        } else {
            int typeStart = type2.getStartPosition();
            int openBrace = intermediate.indexOf('{', typeStart);
            String before = intermediate.substring(0, openBrace + 1);
            String after  = intermediate.substring(openBrace + 1);
            // Strip extra leading blank lines — keep exactly one blank line before first method
            while (after.startsWith("\n\n")) after = after.substring(1);
            return before + "\n" + newField + after;
        }
    }

    // -------------------------------------------------------------------------
    // AST navigation
    // -------------------------------------------------------------------------

    private static VariableDeclarationStatement findVariableDeclarationStatement(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof VariableDeclarationStatement vds) return vds;
            if (current instanceof MethodDeclaration) return null; // crossed method boundary without finding it
            current = current.getParent();
        }
        return null;
    }

    private static TypeDeclaration findEnclosingType(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof TypeDeclaration td
                    && !(td.getParent() instanceof TypeDeclaration)) {
                return td;
            }
            current = current.getParent();
        }
        return null;
    }

    private static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        return ((List<?>) cu.types()).stream()
                .filter(o -> o instanceof TypeDeclaration)
                .map(o -> (TypeDeclaration) o)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No type declaration found."));
    }

    private static String detectIndent(String source, TypeDeclaration type) {
        for (Object bd : type.bodyDeclarations()) {
            if (bd instanceof ASTNode n) {
                int start     = n.getStartPosition();
                int lineStart = source.lastIndexOf('\n', start - 1) + 1;
                String prefix = source.substring(lineStart, start);
                if (!prefix.isBlank()) return prefix;
            }
        }
        return "    ";
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
