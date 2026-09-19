package com.github.pfichtner;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless Extract Constant refactoring using JDT ASTParser.
 *
 * <p>Introduces a {@code private static final} field for the selected expression,
 * inserted at the top of the enclosing type body. Optionally replaces every
 * textually identical occurrence in the class.
 *
 * <p>Supported cases:
 * <ul>
 *   <li>Any non-name, non-assignment expression</li>
 *   <li>Optional: replace all identical occurrences in the enclosing type</li>
 * </ul>
 *
 * <p>Precondition failures:
 * <ul>
 *   <li>Selection is a simple name — nothing to extract</li>
 *   <li>Selection is an assignment expression</li>
 *   <li>Selection is not inside a type declaration</li>
 *   <li>Type of the expression cannot be resolved</li>
 * </ul>
 */
public class JdtExtractConstant {

    /**
     * Extracts the expression at
     * {@code [selectionStart, selectionStart + selectionLength)} into a new
     * {@code private static final} field named {@code constName}.
     *
     * @param source          full source text
     * @param unitName        file name for binding resolution
     * @param selectionStart  start offset of the expression
     * @param selectionLength length of the expression
     * @param constName       name for the introduced constant
     * @param replaceAll      if {@code true}, replace every textually identical
     *                        occurrence in the enclosing type body
     * @return rewritten source
     */
    public static String extractConstant(
            String source, String unitName,
            int selectionStart, int selectionLength,
            String constName, boolean replaceAll) {

        CompilationUnit cu = parse(source, unitName);

        ASTNode node = NodeFinder.perform(cu, selectionStart, selectionLength);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at the given selection.");
        }

        Expression expr = findExpression(node, selectionStart, selectionLength);
        validateExpression(expr);

        ITypeBinding type = expr.resolveTypeBinding();
        String typeName   = resolveTypeName(type, expr);
        String exprText   = source.substring(
                expr.getStartPosition(), expr.getStartPosition() + expr.getLength());

        TypeDeclaration enclosingType = findEnclosingType(expr);

        // Determine insertion point: right after the opening '{' of the type body
        int typeBodyStart = enclosingType.getStartPosition();
        int insertOffset  = source.indexOf('{', typeBodyStart) + 1; // after opening brace

        // Determine indentation for the new field
        // Match the indentation of the first body declaration (or use 4 spaces)
        String fieldIndent = detectFieldIndent(source, enclosingType);
        String fieldDecl   = "\n" + fieldIndent
                + "private static final " + typeName + " " + constName + " = " + exprText + ";";

        // Collect replacement sites within the enclosing type
        int typeStart = enclosingType.getStartPosition();
        int typeEnd   = typeStart + enclosingType.getLength();
        List<int[]> sites = collectSites(
                source, typeStart, typeEnd, exprText, replaceAll, selectionStart, selectionLength);

        // Apply replacements (end to start); all sites are >= selectionStart > insertOffset
        sites.sort((a, b) -> b[0] - a[0]);
        StringBuilder sb = new StringBuilder(source);
        for (int[] site : sites) {
            sb.replace(site[0], site[1], constName);
        }

        // Insert constant declaration (insertOffset < all replacement sites)
        sb.insert(insertOffset, fieldDecl);

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Expression helpers
    // -------------------------------------------------------------------------

    private static Expression findExpression(ASTNode node, int selStart, int selLen) {
        int selEnd = selStart + selLen;
        ASTNode current = node;
        while (current != null) {
            if (current instanceof Expression e
                    && e.getStartPosition() == selStart
                    && e.getStartPosition() + e.getLength() == selEnd) {
                return e;
            }
            current = current.getParent();
        }
        if (node instanceof Expression e) return e;
        throw new IllegalArgumentException(
                "Selection does not correspond to a single expression.");
    }

    private static void validateExpression(Expression expr) {
        if (expr instanceof SimpleName) {
            throw new IllegalArgumentException(
                    "Selection is already a simple name — nothing to extract.");
        }
        if (expr instanceof Assignment) {
            throw new IllegalArgumentException(
                    "Cannot extract an assignment expression.");
        }
        if (expr instanceof VariableDeclarationExpression) {
            throw new IllegalArgumentException(
                    "Cannot extract a variable declaration.");
        }
    }

    private static String resolveTypeName(ITypeBinding type, Expression expr) {
        if (type == null) {
            if (expr instanceof StringLiteral)    return "String";
            if (expr instanceof NumberLiteral nl) return guessNumberType(nl.getToken());
            if (expr instanceof BooleanLiteral)   return "boolean";
            if (expr instanceof CharacterLiteral) return "char";
            throw new IllegalArgumentException(
                    "Cannot resolve the type of the selected expression.");
        }
        return type.isPrimitive() ? type.getName() : type.getName();
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

    private static List<int[]> collectSites(
            String source, int rangeStart, int rangeEnd,
            String exprText, boolean replaceAll,
            int primaryStart, int primaryLen) {
        List<int[]> sites = new ArrayList<>();
        if (!replaceAll) {
            sites.add(new int[]{primaryStart, primaryStart + primaryLen});
            return sites;
        }
        int idx = rangeStart;
        while (idx < rangeEnd) {
            int found = source.indexOf(exprText, idx);
            if (found < 0 || found >= rangeEnd) break;
            int end = found + exprText.length();
            if (isBoundary(source, found, end)) {
                sites.add(new int[]{found, end});
            }
            idx = found + 1;
        }
        if (sites.isEmpty()) sites.add(new int[]{primaryStart, primaryStart + primaryLen});
        return sites;
    }

    private static boolean isBoundary(String source, int start, int end) {
        if (start > 0 && Character.isJavaIdentifierPart(source.charAt(start - 1))) return false;
        return end >= source.length() || !Character.isJavaIdentifierPart(source.charAt(end));
    }

    // -------------------------------------------------------------------------
    // AST navigation
    // -------------------------------------------------------------------------

    private static TypeDeclaration findEnclosingType(ASTNode node) {
        while (node != null && !(node instanceof TypeDeclaration)) node = node.getParent();
        if (!(node instanceof TypeDeclaration td)) {
            throw new IllegalArgumentException(
                    "Selection is not inside a class or interface declaration.");
        }
        return td;
    }

    private static String detectFieldIndent(String source, TypeDeclaration type) {
        // Try to detect indentation from the first existing body declaration
        for (Object o : type.bodyDeclarations()) {
            BodyDeclaration bd = (BodyDeclaration) o;
            int pos = bd.getStartPosition();
            int lineStart = pos;
            while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
            return source.substring(lineStart, pos);
        }
        return "    "; // default: 4 spaces
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
