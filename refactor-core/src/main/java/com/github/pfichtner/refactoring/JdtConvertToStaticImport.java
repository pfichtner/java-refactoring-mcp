package com.github.pfichtner.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;

/**
 * Headless "Convert to static import" refactoring using JDT ASTParser.
 *
 * <p>Given the offset of a qualified static-method call (e.g.
 * {@code Collectors.joining(",")}), removes the qualifier from the call,
 * and adds {@code import static fully.qualified.Class.methodName;} to the
 * import section (skipped if the import is already present).
 *
 * <p>Supported cases:
 * <ul>
 *   <li>Single occurrence (replaceAll = false) — only the targeted call</li>
 *   <li>All occurrences (replaceAll = true) — every qualifying call in the file</li>
 *   <li>Import already present — qualifier(s) removed without duplicating the import</li>
 * </ul>
 *
 * <p>Precondition failures (diagnostic, no file modified):
 * <ul>
 *   <li>Offset is not on a method invocation node</li>
 *   <li>Call has no qualifier (already a bare, unqualified call)</li>
 *   <li>Method is not static</li>
 *   <li>A different static import already uses the same simple name (name clash)</li>
 * </ul>
 */
public class JdtConvertToStaticImport {

    /**
     * Converts the qualified static-method call at {@code offset} to use a
     * static import instead.
     *
     * @param source     full source text
     * @param unitName   file name for binding resolution (e.g. {@code "Foo.java"})
     * @param offset     character offset within the qualifying call expression
     * @param replaceAll if {@code true}, remove the qualifier from every call to
     *                   the same method in the file; if {@code false}, only the
     *                   call at {@code offset}
     * @return rewritten source with qualifier(s) removed and static import added
     * @throws IllegalArgumentException if the conversion cannot be performed
     */
    public static String convertToStaticImport(
            String source, String unitName, int offset, boolean replaceAll) {

        CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(source, unitName);

        MethodInvocation mi = findMethodInvocation(cu, offset);

        if (mi.getExpression() == null) {
            throw new IllegalArgumentException(
                    "The call '" + mi.getName().getIdentifier()
                    + "' is already a bare (unqualified) call — no qualifier to remove.");
        }

        IMethodBinding binding = mi.resolveMethodBinding();
        if (binding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve method binding at offset " + offset
                    + ". Ensure the locator points to a method name.");
        }

        if (!Modifier.isStatic(binding.getModifiers())) {
            throw new IllegalArgumentException(
                    "'" + binding.getName()
                    + "' is not a static method and cannot be statically imported.");
        }

        ITypeBinding declaringClass = binding.getDeclaringClass();
        String className  = declaringClass.getQualifiedName();
        String methodName = binding.getName();
        String importFqn  = className + "." + methodName;
        String importText = "import static " + importFqn + ";";

        // Check for existing static imports: duplicate (ok) or name clash (error)
        @SuppressWarnings("unchecked")
        List<ImportDeclaration> existingImports = cu.imports();
        boolean importAlreadyExists = false;
        for (ImportDeclaration imp : existingImports) {
            if (!imp.isStatic() || imp.isOnDemand()) continue;
            String existingFqn = imp.getName().getFullyQualifiedName();
            if (existingFqn.equals(importFqn)) {
                importAlreadyExists = true;
            } else {
                String existingSimple = existingFqn.substring(existingFqn.lastIndexOf('.') + 1);
                if (existingSimple.equals(methodName)) {
                    throw new IllegalArgumentException(
                            "Cannot add 'import static " + importFqn + "': method name '"
                            + methodName + "' is already statically imported from '"
                            + existingFqn.substring(0, existingFqn.lastIndexOf('.'))
                            + "'. Use the qualified form instead.");
                }
            }
        }

        // Collect qualifier-removal edits
        String methodKey = binding.getMethodDeclaration().getKey();
        List<MethodInvocation> targets = replaceAll
                ? findAllQualifiedCalls(cu, methodKey)
                : List.of(mi);

        List<Edit> edits = new ArrayList<>();
        for (MethodInvocation call : targets) {
            if (call.getExpression() == null) continue;
            // Remove [expressionStart, nameStart) — i.e. "Qualifier."
            int qualStart = call.getExpression().getStartPosition();
            int nameStart = call.getName().getStartPosition();
            edits.add(new Edit(qualStart, nameStart, ""));
        }

        // Add static import if not already present
        if (!importAlreadyExists) {
            int insertPos = importInsertPosition(cu, source, existingImports);
            edits.add(new Edit(insertPos, insertPos, "\n" + importText));
        }

        // Apply edits end-to-start to keep earlier offsets valid
        edits.sort((a, b) -> b.start() - a.start());
        StringBuilder sb = new StringBuilder(source);
        for (Edit ed : edits) sb.replace(ed.start(), ed.end(), ed.replacement());
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int importInsertPosition(
            CompilationUnit cu, String source, List<ImportDeclaration> imports) {
        if (!imports.isEmpty()) {
            ImportDeclaration last = imports.get(imports.size() - 1);
            return last.getStartPosition() + last.getLength();
        }
        if (cu.getPackage() != null) {
            // Position at end of package declaration line (at the '\n')
            int pos = cu.getPackage().getStartPosition() + cu.getPackage().getLength();
            while (pos < source.length() && source.charAt(pos) != '\n') pos++;
            return pos;
        }
        return 0;
    }

    private static MethodInvocation findMethodInvocation(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at offset " + offset + ".");
        }
        while (node != null && !(node instanceof MethodInvocation)) node = node.getParent();
        if (!(node instanceof MethodInvocation mi)) {
            throw new IllegalArgumentException("No method call found at offset " + offset + ".");
        }
        return mi;
    }

    private static List<MethodInvocation> findAllQualifiedCalls(
            CompilationUnit cu, String methodKey) {
        List<MethodInvocation> result = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                if (node.getExpression() == null) return true;
                IMethodBinding b = node.resolveMethodBinding();
                if (b != null && methodKey.equals(b.getMethodDeclaration().getKey())) {
                    result.add(node);
                }
                return true;
            }
        });
        return result;
    }

    private record Edit(int start, int end, String replacement) {}
}
