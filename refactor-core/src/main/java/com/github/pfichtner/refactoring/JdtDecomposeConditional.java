package com.github.pfichtner.refactoring;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;

/**
 * Headless Decompose Conditional refactoring using JDT ASTParser.
 *
 * <p>Extracts the boolean condition of an {@code if}, {@code while}, or {@code for}
 * statement into a private boolean method and replaces the condition with a call
 * to that method.
 *
 * <p>Before:
 * <pre>{@code
 * if (age >= 18 && premium) { ... }
 * }</pre>
 *
 * <p>After:
 * <pre>{@code
 * if (isAdultPremium()) { ... }
 *
 * private boolean isAdultPremium() {
 *     return age >= 18 && premium;
 * }
 * }</pre>
 *
 * <p>Single-file only: the condition and class must be in the same source string.
 *
 * <p>Precondition failures (diagnostic, no source modified):
 * <ul>
 *   <li>No enclosing conditional statement at offset</li>
 *   <li>Condition is trivially simple (single name or literal)</li>
 *   <li>A method with {@code methodName} already exists in the enclosing class</li>
 * </ul>
 */
public class JdtDecomposeConditional {

    /**
     * Decomposes the conditional at {@code offset} in {@code source}.
     *
     * @param source     full source text of the compilation unit
     * @param unitName   simple file name used in error messages (e.g. {@code "Foo.java"})
     * @param offset     character offset within or before the condition expression
     * @param methodName name for the extracted boolean method
     * @return modified source with the condition extracted into {@code methodName}
     */
    public static String decomposeConditional(String source, String unitName, int offset, String methodName) {
        CompilationUnit cu = parse(source, unitName);

        // Locate the conditional statement
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at offset " + offset + ".");
        }

        // Walk up to find the enclosing conditional
        Statement stmt = enclosingConditional(node);
        if (stmt == null) {
            throw new IllegalArgumentException(
                    "No if/while/for statement found at offset " + offset
                    + ". Place cursor within the condition.");
        }

        Expression condition = extractCondition(stmt);
        if (condition == null) {
            throw new IllegalArgumentException(
                    "Statement at offset " + offset + " has no extractable condition.");
        }

        // Reject trivially simple conditions (single name or literal) — nothing to decompose
        if (condition instanceof SimpleName || condition instanceof BooleanLiteral
                || condition instanceof NullLiteral) {
            throw new IllegalArgumentException(
                    "Condition is too simple to decompose: `" + condition + "`. "
                    + "Only compound conditions are eligible.");
        }

        String condText = source.substring(
                condition.getStartPosition(), condition.getStartPosition() + condition.getLength());

        // Detect enclosing type to check for existing method and determine indentation
        TypeDeclaration enclosingType = enclosingType(stmt);
        if (enclosingType != null) {
            for (Object bd : enclosingType.bodyDeclarations()) {
                if (bd instanceof MethodDeclaration md
                        && md.getName().getIdentifier().equals(methodName)) {
                    throw new IllegalArgumentException(
                            "Method '" + methodName + "()' already exists in "
                            + enclosingType.getName().getIdentifier() + ".");
                }
            }
        }

        // Determine indentation of the enclosing type's body declarations
        String indent = detectIndent(source, enclosingType);

        // Build the new method text
        String newMethod = "\n\n" + indent + "private boolean " + methodName + "() {"
                + "\n" + indent + indent + "return " + condText + ";"
                + "\n" + indent + "}\n";

        // Find insertion point: just before the closing brace of the enclosing type
        int insertAt;
        if (enclosingType != null) {
            // Insert before the last '}'  of the type body
            int typeEnd = enclosingType.getStartPosition() + enclosingType.getLength();
            insertAt = source.lastIndexOf('}', typeEnd - 1);
        } else {
            insertAt = source.length();
        }

        // Apply edits (end-to-start: replace condition first, then insert method)
        int condStart = condition.getStartPosition();
        int condEnd   = condStart + condition.getLength();

        StringBuilder sb = new StringBuilder(source);
        // Insert method before closing brace (higher offset first)
        sb.insert(insertAt, newMethod);
        // Replace condition with method call (lower offset second, adjusted)
        // After inserting newMethod, offsets before insertAt are unchanged
        sb.replace(condStart, condEnd, methodName + "()");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // AST helpers
    // -------------------------------------------------------------------------

    private static Statement enclosingConditional(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof IfStatement
                    || current instanceof WhileStatement
                    || current instanceof ForStatement) {
                return (Statement) current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static Expression extractCondition(Statement stmt) {
        return switch (stmt) {
            case IfStatement is    -> is.getExpression();
            case WhileStatement ws -> ws.getExpression();
            case ForStatement fs   -> fs.getExpression();
            default -> null;
        };
    }

    private static TypeDeclaration enclosingType(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof TypeDeclaration td) return td;
            current = current.getParent();
        }
        return null;
    }

    private static String detectIndent(String source, TypeDeclaration enclosingType) {
        if (enclosingType == null) return "    ";
        // Find the first method/field declaration and measure its indentation
        for (Object bd : enclosingType.bodyDeclarations()) {
            if (bd instanceof ASTNode n) {
                int start = n.getStartPosition();
                int lineStart = source.lastIndexOf('\n', start - 1) + 1;
                String prefix = source.substring(lineStart, start);
                if (!prefix.isBlank()) return prefix;
            }
        }
        return "    ";
    }

    private static CompilationUnit parse(String source, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        return (CompilationUnit) parser.createAST(null);
    }
}
