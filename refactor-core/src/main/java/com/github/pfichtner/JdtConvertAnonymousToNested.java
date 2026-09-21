package com.github.pfichtner;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Headless Convert-Anonymous-Class-to-Named-Nested-Class refactoring using JDT ASTParser.
 *
 * <p>Replaces an anonymous class creation expression with an instantiation of a new
 * private named nested class added to the enclosing type:
 *
 * <pre>{@code
 * // Before
 * Runnable r = new Runnable() {
 *     public void run() { doWork(); }
 * };
 *
 * // After: convert(source, "Outer.java", offset, "Worker")
 * Runnable r = new Worker();
 *
 * private class Worker implements Runnable {
 *     public void run() { doWork(); }
 * }
 * }</pre>
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No anonymous class declaration at the given offset</li>
 *   <li>The anonymous class is not inside a named type declaration</li>
 *   <li>The enclosing type already has a member named {@code nestedClassName}</li>
 *   <li>The anonymous class creation has constructor arguments (extends-with-args not supported)</li>
 * </ul>
 *
 * <p>Limitations:
 * <ul>
 *   <li>Only anonymous classes that implement an interface or extend a no-arg constructor
 *       class are supported.</li>
 *   <li>Captured variables from the enclosing scope are not automatically converted to
 *       constructor parameters.</li>
 * </ul>
 */
public class JdtConvertAnonymousToNested {

    /**
     * Converts the anonymous class at {@code offset} to a private named nested class.
     *
     * @param source          full source text
     * @param unitName        file name for binding resolution (e.g. {@code "Outer.java"})
     * @param offset          character offset anywhere inside the anonymous class body
     * @param nestedClassName simple name for the new nested class
     * @return rewritten source
     * @throws IllegalArgumentException if preconditions are not met
     */
    public static String convert(String source, String unitName, int offset, String nestedClassName) {
        CompilationUnit cu = parse(source, unitName);

        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at offset " + offset + ".");
        }

        // Walk UP the tree to find a ClassInstanceCreation that has an anonymous class body
        ClassInstanceCreation cic = findClassInstanceCreationWithAnon(node);
        if (cic == null) {
            throw new IllegalArgumentException(
                    "No anonymous class declaration found at offset " + offset + ".");
        }

        AnonymousClassDeclaration acd = cic.getAnonymousClassDeclaration();

        // Constructor args other than the supertype are not supported
        @SuppressWarnings("unchecked")
        List<Expression> args = cic.arguments();
        if (!args.isEmpty()) {
            throw new IllegalArgumentException(
                    "Anonymous class has constructor arguments — conversion with constructor "
                    + "argument forwarding is not yet supported.");
        }

        TypeDeclaration enclosingType = findEnclosingNamedType(cic);
        if (enclosingType == null) {
            throw new IllegalArgumentException(
                    "Anonymous class is not directly inside a named class declaration.");
        }

        // Check for name collision in the enclosing type
        for (Object bd : enclosingType.bodyDeclarations()) {
            if (bd instanceof TypeDeclaration td
                    && td.getName().getIdentifier().equals(nestedClassName)) {
                throw new IllegalArgumentException(
                        "Type '" + nestedClassName + "' already exists in '"
                        + enclosingType.getName().getIdentifier() + "'.");
            }
        }

        // Determine relationship keyword: implements (interface) or extends (class)
        ITypeBinding typeBinding = cic.getType().resolveBinding();
        boolean isInterface = typeBinding != null && typeBinding.isInterface();
        String keyword = isInterface ? "implements" : "extends";

        String supertypeName = cic.getType().toString();

        // Extract body content of the anonymous class (between { and })
        int acdStart = acd.getStartPosition();
        int acdEnd   = acdStart + acd.getLength();
        String acdBodySource = source.substring(acdStart, acdEnd); // includes { and }

        // Indent to use for nested class members
        String memberIndent = detectIndent(source, enclosingType);
        String bodyIndent   = memberIndent + "    ";

        // Build the nested class text
        String nestedClassText = buildNestedClass(
                nestedClassName, supertypeName, keyword,
                acdBodySource, memberIndent, bodyIndent);

        // CIC extent: "new Runnable() { ... }"
        int cicStart = cic.getStartPosition();
        int cicEnd   = cicStart + cic.getLength();

        // Find insertion point: just before the closing '}' of enclosing type
        int typeBodyEnd = enclosingType.getStartPosition() + enclosingType.getLength();
        int insertAt    = source.lastIndexOf('}', typeBodyEnd - 1);

        // Apply edits in reverse order: insert at high offset first, then replace at low offset
        StringBuilder sb = new StringBuilder(source);
        sb.insert(insertAt, "\n\n" + nestedClassText + "\n");
        sb.replace(cicStart, cicEnd, "new " + nestedClassName + "()");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Text generation
    // -------------------------------------------------------------------------

    private static String buildNestedClass(
            String name, String supertype, String keyword,
            String acdBodySource, String memberIndent, String bodyIndent) {

        // Strip outer braces
        String inner = acdBodySource.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}")) inner = inner.substring(0, inner.length() - 1);

        String[] lines = inner.split("\n", -1);

        // Find the minimum indentation of non-blank lines (to preserve relative structure)
        int minIndent = Integer.MAX_VALUE;
        for (String line : lines) {
            if (!line.isBlank()) {
                int sp = 0;
                while (sp < line.length() && line.charAt(sp) == ' ') sp++;
                minIndent = Math.min(minIndent, sp);
            }
        }
        if (minIndent == Integer.MAX_VALUE) minIndent = 0;

        // Re-indent preserving relative indentation
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            if (line.isBlank()) {
                body.append("\n");
            } else {
                String stripped = line.length() >= minIndent ? line.substring(minIndent) : line.stripLeading();
                body.append(bodyIndent).append(stripped).append("\n");
            }
        }
        String bodyStr = body.toString().stripTrailing();

        return memberIndent + "private class " + name + " " + keyword + " " + supertype + " {"
                + (bodyStr.isEmpty() ? "" : "\n" + bodyStr + "\n" + memberIndent)
                + "}";
    }

    // -------------------------------------------------------------------------
    // AST navigation
    // -------------------------------------------------------------------------

    private static ClassInstanceCreation findClassInstanceCreationWithAnon(ASTNode node) {
        return Stream.iterate(node, Objects::nonNull, ASTNode::getParent)
                .filter(n -> n instanceof ClassInstanceCreation cic
                        && cic.getAnonymousClassDeclaration() != null)
                .map(n -> (ClassInstanceCreation) n)
                .findFirst()
                .orElse(null);
    }

    private static TypeDeclaration findEnclosingNamedType(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof TypeDeclaration td) return td;
            current = current.getParent();
        }
        return null;
    }

    private static String detectIndent(String source, TypeDeclaration enclosingType) {
        for (Object bd : enclosingType.bodyDeclarations()) {
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
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        return (CompilationUnit) parser.createAST(null);
    }
}
