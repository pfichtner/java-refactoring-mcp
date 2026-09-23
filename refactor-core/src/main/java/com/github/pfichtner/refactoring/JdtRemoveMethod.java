package com.github.pfichtner.refactoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;

import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Headless Remove Method refactoring using JDT ASTParser.
 *
 * <p>Removes a method declaration from the target class or interface. When
 * {@code cascade} is {@code true} (the default), all overriding methods in
 * subclasses and all implementing methods in classes that implement the target
 * interface are removed as well.
 *
 * <p>Precondition failures (diagnostic, no files modified):
 * <ul>
 *   <li>No method declaration found at the given offset</li>
 *   <li>Method binding cannot be resolved (cascade mode only)</li>
 * </ul>
 */
public class JdtRemoveMethod {

    /**
     * Removes the method at {@code offset} in {@code sourceFile} and, when
     * {@code cascade} is {@code true}, also removes every overriding or
     * implementing method found in the rest of the project.
     *
     * @param project    Maven/Gradle project providing source roots and classpath
     * @param sourceFile file containing the method to remove
     * @param offset     character offset of any character within the method name
     * @param cascade    if {@code true}, also remove overriding/implementing methods
     * @return map of {@code path → new source} for every changed file
     */
    public static Map<Path, String> removeMethod(
            JavaProject project, Path sourceFile, int offset, boolean cascade)
            throws IOException, InterruptedException {

        if (cascade) {
            return removeMethodWithCascade(project, sourceFile, offset);
        } else {
            return removeMethodSingle(sourceFile, offset);
        }
    }

    // -------------------------------------------------------------------------
    // Single-file path (no binding resolution needed)
    // -------------------------------------------------------------------------

    private static Map<Path, String> removeMethodSingle(Path sourceFile, int offset)
            throws IOException {
        Path absFile = sourceFile.toAbsolutePath().normalize();
        String source = Files.readString(absFile);

        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        MethodDeclaration method = findMethod(cu, offset);
        int[] range = methodRemoveRange(source, method);

        StringBuilder sb = new StringBuilder(source);
        sb.delete(range[0], range[1]);
        return Map.of(absFile, sb.toString());
    }

    // -------------------------------------------------------------------------
    // Cascade path (binding resolution across project)
    // -------------------------------------------------------------------------

    private static Map<Path, String> removeMethodWithCascade(
            JavaProject project, Path sourceFile, int offset)
            throws IOException, InterruptedException {

        String[] classpath   = project.classpath();
        String[] sourcePaths = project.sourceRoots().stream()
                .map(Path::toString).toArray(String[]::new);
        List<Path> allFiles  = JdtProjectSources.collectSourceFiles(project);
        Map<Path, String> sources = JdtProjectSources.readAll(allFiles);

        Map<Path, CompilationUnit> cus = JdtProjectSources.parseAll(allFiles, classpath, sourcePaths);
        Path absTarget = sourceFile.toAbsolutePath().normalize();
        CompilationUnit targetCu = cus.get(absTarget);
        if (targetCu == null) {
            throw new IllegalArgumentException("Source file not found in project: " + sourceFile);
        }

        MethodDeclaration targetMethod = findMethod(targetCu, offset);
        IMethodBinding targetBinding = targetMethod.resolveBinding();
        if (targetBinding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve binding for method at offset " + offset
                    + ". Ensure the project compiles.");
        }
        IMethodBinding targetDecl = targetBinding.getMethodDeclaration();

        // Collect methods to remove per file
        Map<Path, List<MethodDeclaration>> toRemove = new LinkedHashMap<>();
        toRemove.computeIfAbsent(absTarget, k -> new ArrayList<>()).add(targetMethod);

        for (Map.Entry<Path, CompilationUnit> entry : cus.entrySet()) {
            Path filePath = entry.getKey();
            if (filePath.equals(absTarget)) continue;

            List<MethodDeclaration> methods = new ArrayList<>();
            entry.getValue().accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodDeclaration node) {
                    IMethodBinding b = node.resolveBinding();
                    if (b != null && b.overrides(targetDecl)) {
                        methods.add(node);
                    }
                    return true;
                }
            });
            if (!methods.isEmpty()) {
                toRemove.put(filePath, methods);
            }
        }

        // Apply edits per file, end-to-start within each file
        Map<Path, String> changed = new LinkedHashMap<>();
        for (Map.Entry<Path, List<MethodDeclaration>> entry : toRemove.entrySet()) {
            Path filePath = entry.getKey();
            List<MethodDeclaration> methods = entry.getValue();
            String source = sources.get(filePath);

            methods.sort(Comparator.comparingInt(MethodDeclaration::getStartPosition).reversed());

            StringBuilder sb = new StringBuilder(source);
            for (MethodDeclaration m : methods) {
                int[] range = methodRemoveRange(source, m);
                sb.delete(range[0], range[1]);
            }
            changed.put(filePath, sb.toString());
        }

        return changed;
    }

    // -------------------------------------------------------------------------
    // Range calculation
    // -------------------------------------------------------------------------

    /**
     * Returns the source range {@code [start, end)} that covers the method
     * declaration plus its leading indentation/blank-line and trailing newline,
     * so removal leaves no orphaned blank lines.
     */
    static int[] methodRemoveRange(String source, MethodDeclaration method) {
        int start = method.getStartPosition(); // includes Javadoc if present
        int end   = start + method.getLength();

        // Extend start backward: consume indentation up to (and including) the
        // preceding newline, so the blank separator before this method is removed.
        int newStart = start;
        while (newStart > 0 && source.charAt(newStart - 1) != '\n') newStart--;
        if (newStart > 0) newStart--; // include the \n itself

        // Extend end forward: include the trailing newline after the closing '}'
        if (end < source.length() && source.charAt(end) == '\n') end++;

        return new int[]{newStart, end};
    }

    // -------------------------------------------------------------------------
    // AST navigation
    // -------------------------------------------------------------------------

    private static MethodDeclaration findMethod(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at offset " + offset);
        }
        while (node != null) {
            if (node instanceof MethodDeclaration md) {
                return md;
            }
            node = node.getParent();
        }
        throw new IllegalArgumentException(
                "No method declaration found at offset " + offset
                + ". Place the cursor within a method name or signature.");
    }

    // -------------------------------------------------------------------------
    // Infrastructure (mirrors JdtRemoveParam)
    // -------------------------------------------------------------------------
}
