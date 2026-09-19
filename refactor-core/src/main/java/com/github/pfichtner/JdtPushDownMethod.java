package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Headless Push-Down Method refactoring using JDT ASTParser.
 *
 * <p>Moves a method declaration from a class down into every direct subclass
 * that is found within the project source roots. The method is removed from
 * the superclass and added to each identified subclass.
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No method at the given offset</li>
 *   <li>No direct subclasses found in the project</li>
 *   <li>Any subclass already declares the method with the same name and parameter count</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Subclasses are found by scanning for {@code extends ClassName}; fully-qualified
 *       {@code extends} clauses (e.g. {@code extends com.example.Animal}) are not matched.</li>
 *   <li>Only direct subclasses (single-level inheritance) are pushed to.</li>
 *   <li>{@code @Override} annotations on the method are copied verbatim.</li>
 * </ul>
 */
public class JdtPushDownMethod {

    /**
     * Pushes a method down from a class to all direct subclasses in the project.
     *
     * @param project    Maven project used to enumerate source roots
     * @param sourceFile file containing the class with the method to push down
     * @param offset     character offset in {@code sourceFile} pointing into the method
     * @return {@code path → new source} for the superclass and every subclass found
     */
    public static Map<Path, String> pushDown(
            MavenProject project, Path sourceFile, int offset)
            throws IOException, InterruptedException {

        Path absSource = sourceFile.toAbsolutePath().normalize();
        String source = Files.readString(absSource);
        CompilationUnit cu = parse(source, absSource.getFileName().toString());

        MethodDeclaration method = findMethodAt(cu, offset);
        if (method == null) {
            throw new IllegalArgumentException(
                    "No method declaration found at the given offset.");
        }

        TypeDeclaration type = findPrimaryType(cu);
        String className = type.getName().getIdentifier();

        String methodName = method.getName().getIdentifier();
        int paramCount    = method.parameters().size();

        // Find all direct subclasses in the project
        List<Path> subclassFiles = findSubclasses(project, absSource, className);
        if (subclassFiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "No direct subclasses of '" + className
                    + "' found in project source roots.");
        }

        // Validate no subclass already has a conflicting method
        for (Path sub : subclassFiles) {
            String subSource = Files.readString(sub);
            CompilationUnit subCu = parse(subSource, sub.getFileName().toString());
            TypeDeclaration subType = findPrimaryType(subCu);
            for (Object o : subType.bodyDeclarations()) {
                if (o instanceof MethodDeclaration md
                        && !md.isConstructor()
                        && md.getName().getIdentifier().equals(methodName)
                        && md.parameters().size() == paramCount) {
                    throw new IllegalArgumentException(
                            "Subclass '" + sub.getFileName() + "' already declares '"
                            + methodName + "' with " + paramCount + " parameter(s).");
                }
            }
        }

        String rawMethod = source.substring(
                method.getStartPosition(), method.getStartPosition() + method.getLength());

        // Build result map: superclass first, then subclasses
        Map<Path, String> result = new LinkedHashMap<>();
        result.put(absSource, JdtPullUpMethod.removeMethod(source, method));

        for (Path sub : subclassFiles) {
            String subSource = Files.readString(sub);
            CompilationUnit subCu = parse(subSource, sub.getFileName().toString());
            TypeDeclaration subType = findPrimaryType(subCu);
            result.put(sub, JdtPullUpMethod.insertMethod(subSource, subType, rawMethod));
        }
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

    private static List<Path> findSubclasses(
            MavenProject project, Path excludeFile, String superclassName)
            throws IOException, InterruptedException {
        List<Path> result = new ArrayList<>();
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            List<Path> javaFiles;
            try (var stream = Files.walk(root)) {
                javaFiles = stream
                        .filter(p -> p.toString().endsWith(".java"))
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
            }
            for (Path file : javaFiles) {
                if (file.equals(excludeFile)) continue;
                String src = Files.readString(file);
                if (containsExtends(src, superclassName)) {
                    result.add(file);
                }
            }
        }
        return result;
    }

    // Word-boundary check so "extends Animal" doesn't match "extends AnimalAdapter"
    private static boolean containsExtends(String source, String superclassName) {
        String search = "extends " + superclassName;
        int idx = source.indexOf(search);
        while (idx >= 0) {
            int afterIdx = idx + search.length();
            if (afterIdx >= source.length()
                    || !Character.isJavaIdentifierPart(source.charAt(afterIdx))) {
                return true;
            }
            idx = source.indexOf(search, idx + 1);
        }
        return false;
    }

    static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        for (Object o : cu.types()) {
            if (o instanceof TypeDeclaration td) return td;
        }
        throw new IllegalArgumentException("No type declaration found in source.");
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
