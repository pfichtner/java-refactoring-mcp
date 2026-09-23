package com.github.pfichtner.refactoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Headless Move Method refactoring using JDT ASTParser.
 *
 * <p>Moves a method declaration from one class to another class within the same project.
 * The target class must exist in the project source roots.
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No method at the given offset</li>
 *   <li>Target class source not found in project</li>
 *   <li>Target class already has a method with the same name and parameter count</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>References to {@code this} in the moved method remain unchanged — the caller
 *       is responsible for any semantic adjustments.</li>
 *   <li>Call sites in other files are not updated.</li>
 * </ul>
 */
public class JdtMoveMethod {

    /**
     * Moves a method from one class to another class within the project.
     * Equivalent to {@link #moveMethod(JavaProject, Path, int, String, boolean)} with {@code widenVisibility=true}.
     */
    public static Map<Path, String> moveMethod(
            JavaProject project, Path sourceFile, int offset, String targetClass)
            throws IOException, InterruptedException {
        return moveMethod(project, sourceFile, offset, targetClass, true);
    }

    /**
     * Moves a method from one class to another class within the project.
     *
     * @param project         project used to enumerate source roots
     * @param sourceFile      file containing the class with the method to move
     * @param offset          character offset in {@code sourceFile} pointing into the method
     * @param targetClass     fully-qualified name of the target class (e.g. {@code "com.example.Report"})
     * @param widenVisibility if {@code true} and the method is {@code private}, its modifier is
     *                        widened to the minimum required: package-private when source and target
     *                        share the same package, {@code public} when they are in different packages
     * @return {@code path → new source} for the target file and the source file (2 entries)
     */
    public static Map<Path, String> moveMethod(
            JavaProject project, Path sourceFile, int offset, String targetClass,
            boolean widenVisibility)
            throws IOException, InterruptedException {

        Path absSource = sourceFile.toAbsolutePath().normalize();
        String source = Files.readString(absSource);
        CompilationUnit cu = parse(source, absSource.getFileName().toString());

        MethodDeclaration method = findMethodAt(cu, offset);
        if (method == null) {
            throw new IllegalArgumentException(
                    "No method declaration found at the given offset.");
        }

        Path targetFile = JdtPullUpField.findClassFileByFqn(project, targetClass);
        if (targetFile == null) {
            throw new IllegalArgumentException(
                    "Source file for class '" + targetClass
                    + "' not found in project source roots.");
        }

        String targetSource = Files.readString(targetFile);
        CompilationUnit targetCu = parse(targetSource, targetFile.getFileName().toString());
        TypeDeclaration targetType = JdtPullUpMethod.findPrimaryType(targetCu);

        String methodName = method.getName().getIdentifier();
        int paramCount = method.parameters().size();
        if (targetType.bodyDeclarations().stream().anyMatch(o -> o instanceof MethodDeclaration md
                && !md.isConstructor()
                && md.getName().getIdentifier().equals(methodName)
                && md.parameters().size() == paramCount))
            throw new IllegalArgumentException(
                    "Class '" + targetClass + "' already declares '"
                    + methodName + "' with " + paramCount + " parameter(s).");

        String rawMethod = source.substring(
                method.getStartPosition(), method.getStartPosition() + method.getLength());
        if (widenVisibility) {
            String sourcePackage = JdtPullUpField.packageOf(cu);
            String targetPackage = JdtPullUpField.packageOf(targetCu);
            String widenTo = sourcePackage.equals(targetPackage) ? "" : "public";
            rawMethod = JdtPullUpField.widenModifier(method, rawMethod, widenTo);
        }

        String newSourceSource = JdtPullUpMethod.removeMethod(source, method);
        String newTargetSource = JdtPullUpMethod.insertMethod(targetSource, targetType, rawMethod);

        Map<Path, String> result = new LinkedHashMap<>();
        result.put(targetFile, newTargetSource);
        result.put(absSource, newSourceSource);
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static MethodDeclaration findMethodAt(CompilationUnit cu, int offset) {
        MethodDeclaration[] found = {null};
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (!(node.getParent() instanceof TypeDeclaration parent)) return true;
                if (!(parent.getParent() instanceof CompilationUnit)) return true;
                int start = node.getStartPosition();
                int end   = start + node.getLength();
                if (offset >= start && offset < end) found[0] = node;
                return true;
            }
        });
        return found[0];
    }

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
