package com.github.pfichtner.refactoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Headless Pull-Up Method refactoring using JDT ASTParser.
 *
 * <p>Moves a method declaration from a subclass to its direct superclass.
 * The superclass must exist within the project source roots.
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No method at the given offset</li>
 *   <li>Enclosing class has no explicit {@code extends} clause</li>
 *   <li>Superclass source not found in project</li>
 *   <li>Superclass already has a method with the same name and parameter count</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Superclass resolved via import declarations first, then same-package assumption;
 *       wildcard imports ({@code import pkg.*}) are not resolved.</li>
 *   <li>{@code @Override} annotations are copied verbatim — remove manually if unneeded.</li>
 *   <li>Method references to {@code this} fields absent in the superclass compile but
 *       may fail at runtime; caller's responsibility.</li>
 * </ul>
 */
public class JdtPullUpMethod {

    /**
     * Pulls a method up from a subclass to its direct superclass.
     * Equivalent to {@link #pullUp(JavaProject, Path, int, boolean)} with {@code widenVisibility=true}.
     */
    public static Map<Path, String> pullUp(
            JavaProject project, Path sourceFile, int offset)
            throws IOException, InterruptedException {
        return pullUp(project, sourceFile, offset, true);
    }

    /**
     * Pulls a method up from a subclass to its direct superclass.
     *
     * @param project         Maven project used to enumerate source roots
     * @param sourceFile      file containing the subclass with the method to pull up
     * @param offset          character offset in {@code sourceFile} pointing into the method
     * @param widenVisibility if {@code true} and the method is {@code private}, its modifier
     *                        is changed to {@code protected} in the superclass
     * @return {@code path → new source} for the superclass and the subclass (2 entries)
     */
    public static Map<Path, String> pullUp(
            JavaProject project, Path sourceFile, int offset, boolean widenVisibility)
            throws IOException, InterruptedException {

        Path absSource = sourceFile.toAbsolutePath().normalize();
        String source = Files.readString(absSource);
        CompilationUnit cu = parse(source, absSource.getFileName().toString());

        MethodDeclaration method = findMethodAt(cu, offset);
        if (method == null) {
            throw new IllegalArgumentException(
                    "No method declaration found at the given offset.");
        }

        TypeDeclaration type = enclosingType(method);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Method is not a direct member of a class declaration.");
        }

        Type superType = type.getSuperclassType();
        if (superType == null) {
            throw new IllegalArgumentException(
                    "Class '" + type.getName().getIdentifier()
                    + "' has no explicit superclass — cannot pull up.");
        }
        String superSimpleName = extractSimpleName(superType);
        String superFqn = JdtPullUpField.resolveClassFqn(cu, superSimpleName);

        Path superFile = JdtPullUpField.findClassFileByFqn(project, superFqn);
        if (superFile == null) superFile = findClassFile(project, superSimpleName);
        if (superFile == null) {
            throw new IllegalArgumentException(
                    "Source file for superclass '" + superFqn
                    + "' not found in project source roots.");
        }

        String superSource = Files.readString(superFile);
        CompilationUnit superCu = parse(superSource, superFile.getFileName().toString());
        TypeDeclaration superTypeDecl = findPrimaryType(superCu);

        String methodName = method.getName().getIdentifier();
        int paramCount = method.parameters().size();
        if (superTypeDecl.bodyDeclarations().stream().anyMatch(o -> o instanceof MethodDeclaration md
                && !md.isConstructor()
                && md.getName().getIdentifier().equals(methodName)
                && md.parameters().size() == paramCount))
            throw new IllegalArgumentException(
                    "Superclass '" + superFqn + "' already declares '"
                    + methodName + "' with " + paramCount + " parameter(s).");

        String rawMethod = source.substring(
                method.getStartPosition(), method.getStartPosition() + method.getLength());
        if (widenVisibility) rawMethod = JdtPullUpField.widenModifier(method, rawMethod, "protected");

        String newSubclassSource = removeMethod(source, method);
        String newSuperSource    = insertMethod(superSource, superTypeDecl, rawMethod);

        Map<Path, String> result = new LinkedHashMap<>();
        result.put(superFile, newSuperSource);
        result.put(absSource, newSubclassSource);
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

    private static TypeDeclaration enclosingType(MethodDeclaration method) {
        if (method.getParent() instanceof TypeDeclaration td) return td;
        return null;
    }

    private static String extractSimpleName(Type type) {
        String name = type.toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static Path findClassFile(JavaProject project, String simpleName)
            throws IOException, InterruptedException {
        String fileName = simpleName + ".java";
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.walk(root)) {
                Optional<Path> found = stream
                        .filter(p -> p.getFileName().toString().equals(fileName))
                        .findFirst();
                if (found.isPresent()) return found.get().toAbsolutePath().normalize();
            }
        }
        return null;
    }

    static String removeMethod(String source, MethodDeclaration method) {
        int start = method.getStartPosition();
        int end   = start + method.getLength();

        // Walk start back to the beginning of its line
        int lineStart = start;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;

        // Walk end forward to consume the trailing newline
        while (end < source.length() && source.charAt(end) != '\n') end++;
        if (end < source.length()) end++;

        // Also consume one preceding blank line if present
        if (lineStart >= 2
                && source.charAt(lineStart - 1) == '\n'
                && source.charAt(lineStart - 2) == '\n') {
            lineStart--;
        }

        return source.substring(0, lineStart) + source.substring(end);
    }

    static String insertMethod(String targetSource, TypeDeclaration targetType, String rawMethod) {
        int insertAt  = targetType.getStartPosition() + targetType.getLength() - 1;
        String before = targetSource.substring(0, insertAt);
        String after  = targetSource.substring(insertAt);

        String reindented = reindent(rawMethod, "    ");

        return before.stripTrailing() + "\n\n" + reindented + "\n" + after;
    }

    static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        return ((List<?>) cu.types()).stream()
                .filter(o -> o instanceof TypeDeclaration)
                .map(o -> (TypeDeclaration) o)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No type declaration found in source."));
    }

    static String reindent(String text, String newIndent) {
        String[] lines = text.split("\n", -1);
        if (lines.length == 0) return newIndent;

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
                out.append(newIndent).append(line.stripLeading());
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
