package com.github.pfichtner;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;

/**
 * Headless Extract Variable refactoring using JDT ASTParser.
 *
 * <p>Introduces a new local variable for the selected expression, optionally
 * replacing all occurrences of the same literal text within the enclosing
 * statement block.
 *
 * <p>Supported cases:
 * <ul>
 *   <li>Any expression that is not already a simple name or declaration</li>
 *   <li>Optional: replace all identical textual occurrences in the same block</li>
 * </ul>
 *
 * <p>Precondition failures (diagnostic, no file modified):
 * <ul>
 *   <li>Selection is a simple name — nothing to extract</li>
 *   <li>Selection is not inside a statement block</li>
 *   <li>Type of the expression cannot be resolved</li>
 * </ul>
 */
public class JdtExtractVariable {

    /**
     * Extracts the expression at
     * {@code [selectionStart, selectionStart + selectionLength)} into a new
     * local variable named {@code varName} and returns the rewritten source.
     *
     * @param source           full source text
     * @param unitName         file name for binding resolution (e.g. {@code "Foo.java"})
     * @param selectionStart   start offset of the expression
     * @param selectionLength  length of the expression
     * @param varName          name for the introduced variable
     * @param replaceAll       if {@code true}, replace every textually identical
     *                         occurrence in the enclosing block, not just the selection
     * @return rewritten source
     * @throws IllegalArgumentException if the selection cannot be extracted
     */
    public static String extractVariable(
            String source, String unitName,
            int selectionStart, int selectionLength,
            String varName, boolean replaceAll) {

        CompilationUnit cu = parse(source, unitName);

        // Find the selected expression
        ASTNode node = NodeFinder.perform(cu, selectionStart, selectionLength);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at the given selection.");
        }

        // Walk up until we hit an Expression that covers exactly the selection
        Expression expr = findExpression(node, selectionStart, selectionLength);

        validateExpression(expr, selectionStart);

        // Resolve the type
        ITypeBinding type = expr.resolveTypeBinding();
        String typeName = resolveTypeName(type, expr);

        // Extract the expression text
        String exprText = source.substring(
                expr.getStartPosition(), expr.getStartPosition() + expr.getLength());

        // Find the enclosing statement (to insert the declaration before it)
        Statement enclosingStmt = findEnclosingStatement(expr);

        // Find the enclosing block (to scope the replacement)
        Block enclosingBlock = findEnclosingBlock(enclosingStmt);

        // Collect replacement sites
        List<int[]> sites = collectSites(source, enclosingBlock, exprText, replaceAll,
                selectionStart, selectionLength);

        // Find the declaration insertion point: start of the enclosing statement's line
        int stmtStart = enclosingStmt.getStartPosition();
        int lineStart = stmtStart;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
        String indent = source.substring(lineStart, stmtStart);

        String declaration = indent + typeName + " " + varName + " = " + exprText + ";\n";

        // Apply replacements from end to start, then insert declaration
        sites.sort((a, b) -> b[0] - a[0]); // largest offset first
        StringBuilder sb = new StringBuilder(source);
        for (int[] site : sites) {
            sb.replace(site[0], site[1], varName);
        }
        // Insert declaration before enclosingStmt's line (position unchanged: before all sites)
        sb.insert(lineStart, declaration);

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Expression finding
    // -------------------------------------------------------------------------

    private static Expression findExpression(
            ASTNode node, int selStart, int selLen) {
        int selEnd = selStart + selLen;
        // Walk up to find an Expression that matches the selection exactly
        ASTNode current = node;
        while (current != null) {
            if (current instanceof Expression e
                    && e.getStartPosition() == selStart
                    && e.getStartPosition() + e.getLength() == selEnd) {
                return e;
            }
            current = current.getParent();
        }
        // If no exact match, try the node itself if it is an expression
        if (node instanceof Expression e) return e;
        throw new IllegalArgumentException(
                "Selection does not correspond to a single expression.");
    }

    private static void validateExpression(Expression expr, int offset) {
        if (expr instanceof SimpleName) {
            throw new IllegalArgumentException(
                    "Selection is already a simple name — nothing to extract.");
        }
        if (expr instanceof VariableDeclarationExpression) {
            throw new IllegalArgumentException(
                    "Cannot extract a variable declaration.");
        }
        if (expr instanceof Assignment) {
            throw new IllegalArgumentException(
                    "Cannot extract an assignment expression.");
        }
    }

    // -------------------------------------------------------------------------
    // Type resolution
    // -------------------------------------------------------------------------

    private static String resolveTypeName(ITypeBinding type, Expression expr) {
        if (type == null) {
            // Fallback heuristics for common literals
            if (expr instanceof StringLiteral)    return "String";
            if (expr instanceof NumberLiteral nl) return guessNumberType(nl.getToken());
            if (expr instanceof BooleanLiteral)   return "boolean";
            if (expr instanceof CharacterLiteral) return "char";
            throw new IllegalArgumentException(
                    "Cannot resolve the type of the selected expression.");
        }
        return type.getName(); // simple name; adequate for non-generic types
    }

    private static String guessNumberType(String token) {
        if (token.endsWith("L") || token.endsWith("l")) return "long";
        if (token.endsWith("F") || token.endsWith("f")) return "float";
        if (token.endsWith("D") || token.endsWith("d")) return "double";
        if (token.contains(".")) return "double";
        return "int";
    }

    // -------------------------------------------------------------------------
    // Replacement site collection
    // -------------------------------------------------------------------------

    /**
     * Returns all {@code [start, end)} ranges in {@code block} that contain
     * exactly {@code exprText}. If {@code replaceAll} is false, returns only
     * the primary selection.
     */
    private static List<int[]> collectSites(
            String source, Block block,
            String exprText, boolean replaceAll,
            int primaryStart, int primaryLen) {

        List<int[]> sites = new ArrayList<>();
        if (!replaceAll) {
            sites.add(new int[]{primaryStart, primaryStart + primaryLen});
            return sites;
        }

        int blockStart = block.getStartPosition();
        int blockEnd   = blockStart + block.getLength();
        int idx = blockStart;
        while (idx < blockEnd) {
            int found = source.indexOf(exprText, idx);
            if (found < 0 || found >= blockEnd) break;
            int end = found + exprText.length();
            // Ensure it's a whole token (not a substring of a larger identifier)
            if (isBoundary(source, found, end)) {
                sites.add(new int[]{found, end});
            }
            idx = found + 1;
        }
        if (sites.isEmpty()) {
            sites.add(new int[]{primaryStart, primaryStart + primaryLen});
        }
        return sites;
    }

    /** True if the match at [start, end) is not inside a larger identifier. */
    private static boolean isBoundary(String source, int start, int end) {
        if (start > 0) {
            char before = source.charAt(start - 1);
            if (Character.isJavaIdentifierPart(before)) return false;
        }
        if (end < source.length()) {
            char after = source.charAt(end);
            return !Character.isJavaIdentifierPart(after);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // AST navigation
    // -------------------------------------------------------------------------

    private static Statement findEnclosingStatement(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof Statement s && current.getParent() instanceof Block) {
                return s;
            }
            current = current.getParent();
        }
        throw new IllegalArgumentException(
                "Selection is not inside a statement within a block.");
    }

    private static Block findEnclosingBlock(Statement stmt) {
        if (stmt.getParent() instanceof Block b) return b;
        throw new IllegalArgumentException(
                "Enclosing statement is not inside a block.");
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
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        return (CompilationUnit) parser.createAST(null);
    }
}
