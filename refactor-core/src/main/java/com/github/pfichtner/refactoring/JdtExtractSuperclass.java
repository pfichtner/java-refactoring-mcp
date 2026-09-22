package com.github.pfichtner.refactoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;

/**
 * Headless Extract Superclass refactoring using JDT ASTParser.
 *
 * <p>Moves selected public non-static methods from a class into a new
 * {@code public abstract} superclass and makes the original class extend it.
 *
 * <p>Differences from {@link JdtExtractInterface}:
 * <ul>
 *   <li>Method <em>bodies</em> are moved (not just signatures)</li>
 *   <li>Methods are <em>removed</em> from the original class (they are inherited)</li>
 *   <li>Produces {@code extends}, not {@code implements}</li>
 * </ul>
 *
 * <p>Precondition failures:
 * <ul>
 *   <li>Class already extends another class (superclass chaining not supported)</li>
 *   <li>No public non-static methods to move</li>
 *   <li>Named method not found</li>
 * </ul>
 */
public class JdtExtractSuperclass {

    /**
     * Result of an extract-superclass operation.
     *
     * @param modifiedClassSource class source with the moved methods removed and
     *                            {@code extends SuperclassName} added
     * @param superclassSource    full source of the new abstract superclass file
     */
    public record Result(String modifiedClassSource, String superclassSource) {}

    /**
     * Extracts a superclass from the given class source.
     *
     * @param classSource     full source text of the class file
     * @param unitName        file name (e.g. {@code "Calculator.java"})
     * @param superclassName  simple name for the new abstract superclass
     * @param methodNames     methods to move; empty = all public non-static methods
     * @return {@link Result} with the modified class and the new superclass source
     */
    public static Result extractSuperclass(
            String classSource, String unitName,
            String superclassName, List<String> methodNames) {

        CompilationUnit cu = parse(classSource, unitName);
        TypeDeclaration typeDecl = findPrimaryType(cu);

        if (typeDecl.getSuperclassType() != null) {
            throw new IllegalArgumentException(
                    "Cannot extract superclass: '"
                    + typeDecl.getName().getIdentifier()
                    + "' already extends '"
                    + typeDecl.getSuperclassType().toString()
                    + "'. Superclass chaining is not supported.");
        }

        @SuppressWarnings("unchecked")
        List<MethodDeclaration> allPublic = ((List<Object>) typeDecl.bodyDeclarations()).stream()
                .filter(o -> o instanceof MethodDeclaration md
                        && isPublicNonStatic(md) && !md.isConstructor())
                .map(o -> (MethodDeclaration) o)
                .collect(Collectors.toList());
        if (allPublic.isEmpty()) {
            throw new IllegalArgumentException(
                    "No public non-static methods found — nothing to move.");
        }

        List<MethodDeclaration> toMove;
        if (methodNames.isEmpty()) {
            toMove = allPublic;
        } else {
            Set<String> wanted = Set.copyOf(methodNames);
            toMove = allPublic.stream()
                    .filter(m -> wanted.contains(m.getName().getIdentifier()))
                    .collect(Collectors.toList());
            Set<String> found = toMove.stream()
                    .map(m -> m.getName().getIdentifier())
                    .collect(Collectors.toSet());
            Set<String> missing = wanted.stream().filter(n -> !found.contains(n))
                    .collect(Collectors.toSet());
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("Methods not found: " + missing);
            }
        }

        // -------------------------------------------------------------------------
        // Generate superclass source
        // -------------------------------------------------------------------------
        String pkg = cu.getPackage() != null
                ? "package " + cu.getPackage().getName().getFullyQualifiedName() + ";\n\n"
                : "";

        StringBuilder superSrc = new StringBuilder();
        superSrc.append(pkg);
        superSrc.append("public abstract class ").append(superclassName).append(" {\n");
        superSrc.append(toMove.stream()
                .map(m -> reindent(classSource.substring(
                        m.getStartPosition(), m.getStartPosition() + m.getLength()), "    ") + "\n")
                .collect(Collectors.joining()));
        superSrc.append("}\n");

        // -------------------------------------------------------------------------
        // Remove moved methods from original class and add extends clause
        // -------------------------------------------------------------------------
        // Collect line ranges to remove (sorted largest-to-smallest for safe deletion)
        List<int[]> removals = new ArrayList<>();
        for (MethodDeclaration m : toMove) {
            int mStart = m.getStartPosition();
            int mEnd   = mStart + m.getLength();
            // Extend to full line(s)
            int lineStart = mStart;
            while (lineStart > 0 && classSource.charAt(lineStart - 1) != '\n') lineStart--;
            int lineEnd = mEnd;
            while (lineEnd < classSource.length() && classSource.charAt(lineEnd) != '\n') lineEnd++;
            if (lineEnd < classSource.length()) lineEnd++; // include trailing newline
            removals.add(new int[]{lineStart, lineEnd});
        }
        removals.sort((a, b) -> b[0] - a[0]);

        StringBuilder sb = new StringBuilder(classSource);
        for (int[] r : removals) sb.delete(r[0], r[1]);

        // Add extends clause before the class body opening brace.
        // Compute bracePos in the ORIGINAL classSource (before any removals) using safe
        // AST end positions — method removals only affect content after '{', so the
        // position is unchanged in `modified`.
        String modified = sb.toString();
        int bracePos = findClassBodyBrace(classSource, typeDecl);
        int wsStart = bracePos;
        while (wsStart > 0 && modified.charAt(wsStart - 1) == ' ') wsStart--;
        sb = new StringBuilder(modified);
        sb.replace(wsStart, bracePos, " extends " + superclassName + " ");

        return new Result(sb.toString(), superSrc.toString());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Re-indents a method text whose declaration starts at column 0 (JDT strips
     * leading whitespace from {@link MethodDeclaration#getStartPosition()}).
     *
     * <p>Line 0 (the declaration) gets {@code newIndent} prepended.
     * Lines 1+ have their shared base indentation (minimum non-blank indent)
     * stripped, then {@code newIndent} prepended, preserving relative indentation.
     */
    private static String reindent(String text, String newIndent) {
        String[] lines = text.split("\n", -1);
        if (lines.length == 0) return newIndent;

        // Find base indentation from body/closing lines (lines 1+)
        int baseIndent = Integer.MAX_VALUE;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                int sp = 0;
                while (sp < lines[i].length() && lines[i].charAt(sp) == ' ') sp++;
                baseIndent = Math.min(baseIndent, sp);
            }
        }
        if (baseIndent == Integer.MAX_VALUE) baseIndent = 0;

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == 0) {
                out.append(newIndent).append(line);
            } else if (line.isBlank()) {
                out.append("");
            } else {
                String stripped = line.length() >= baseIndent ? line.substring(baseIndent) : line;
                out.append(newIndent).append(stripped);
            }
            if (i < lines.length - 1) out.append("\n");
        }
        return out.toString();
    }

    private static boolean isPublicNonStatic(MethodDeclaration m) {
        boolean isPublic = false;
        for (Object o : m.modifiers()) {
            if (o instanceof Modifier mod) {
                if (mod.isStatic()) return false;
                if (mod.isPublic()) isPublic = true;
            }
        }
        return isPublic;
    }

    /**
     * Finds the class body opening '{' in {@code source} using safe AST end positions,
     * scanning from after the class name, type parameters, and superinterface types.
     * This avoids false hits from '{' inside annotations on the class declaration.
     */
    @SuppressWarnings("unchecked")
    private static int findClassBodyBrace(String source, TypeDeclaration typeDecl) {
        int searchFrom = typeDecl.getName().getStartPosition() + typeDecl.getName().getLength();
        List<TypeParameter> typeParams = typeDecl.typeParameters();
        if (!typeParams.isEmpty()) {
            TypeParameter last = typeParams.get(typeParams.size() - 1);
            searchFrom = last.getStartPosition() + last.getLength();
        }
        List<Type> superInterfaces = typeDecl.superInterfaceTypes();
        if (!superInterfaces.isEmpty()) {
            Type last = superInterfaces.get(superInterfaces.size() - 1);
            searchFrom = last.getStartPosition() + last.getLength();
        }
        return source.indexOf('{', searchFrom);
    }

    private static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        @SuppressWarnings("unchecked")
        List<Object> types = cu.types();
        return types.stream()
                .filter(o -> o instanceof TypeDeclaration)
                .map(o -> (TypeDeclaration) o)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No type declaration found."));
    }

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
