package com.github.pfichtner;

import com.github.pfichtner.project.JavaProject;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Headless Push-Down Field refactoring using JDT ASTParser.
 *
 * <p>Moves a field declaration from a class down to every direct subclass
 * found within the project source roots. The field is removed from the
 * superclass and added to each subclass.
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No field at the given offset</li>
 *   <li>No direct subclasses found in project</li>
 *   <li>Any subclass already declares a field with the same name</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Subclasses resolved by checking import declarations and package context; wildcard
 *       imports ({@code import pkg.*}) are not resolved.</li>
 *   <li>Only single-level (direct) subclasses are pushed to.</li>
 *   <li>Multi-fragment declarations (e.g. {@code int x, y;}) are pushed as a unit.</li>
 * </ul>
 */
public class JdtPushDownField {

    /**
     * Pushes a field down from a class to every direct subclass in the project.
     *
     * @param project    Maven project used to enumerate source roots
     * @param sourceFile file containing the class with the field to push down
     * @param offset     character offset in {@code sourceFile} pointing into the field
     * @return {@code path → new source} for the superclass and all subclasses found
     */
    public static Map<Path, String> pushDown(
            JavaProject project, Path sourceFile, int offset)
            throws IOException, InterruptedException {

        Path absSource = sourceFile.toAbsolutePath().normalize();
        String source = Files.readString(absSource);
        CompilationUnit cu = JdtPullUpField.parse(source, absSource.getFileName().toString());

        FieldDeclaration field = JdtPullUpField.findFieldAt(cu, offset);
        if (field == null) {
            throw new IllegalArgumentException(
                    "No field declaration found at the given offset.");
        }

        TypeDeclaration type = JdtPullUpField.enclosingType(field);
        String className = type.getName().getIdentifier();
        String classFqn  = JdtPullUpField.primaryTypeFqn(cu);

        List<Path> subclassFiles = findSubclasses(project, absSource, className, classFqn);
        if (subclassFiles.isEmpty()) {
            throw new IllegalArgumentException(
                    "No direct subclasses of '" + className
                    + "' found in project source roots.");
        }

        // Validate: no subclass already has a field with the same name
        Set<String> fieldNames = JdtPullUpField.fieldNames(field);
        for (Path sub : subclassFiles) {
            String subSource = Files.readString(sub);
            CompilationUnit subCu = JdtPullUpField.parse(subSource, sub.getFileName().toString());
            TypeDeclaration subType = JdtPullUpField.findPrimaryType(subCu);
            for (Object o : subType.bodyDeclarations()) {
                if (o instanceof FieldDeclaration fd) {
                    for (String name : JdtPullUpField.fieldNames(fd)) {
                        if (fieldNames.contains(name)) {
                            throw new IllegalArgumentException(
                                    "Subclass '" + sub.getFileName()
                                    + "' already declares a field '" + name + "'.");
                        }
                    }
                }
            }
        }

        String rawField = source.substring(
                field.getStartPosition(), field.getStartPosition() + field.getLength());

        Map<Path, String> result = new LinkedHashMap<>();
        result.put(absSource, JdtPullUpField.removeField(source, field));

        for (Path sub : subclassFiles) {
            String subSource = Files.readString(sub);
            CompilationUnit subCu = JdtPullUpField.parse(subSource, sub.getFileName().toString());
            TypeDeclaration subType = JdtPullUpField.findPrimaryType(subCu);
            result.put(sub, JdtPullUpField.insertField(subSource, subType, rawField));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<Path> findSubclasses(
            JavaProject project, Path excludeFile, String superclassSimpleName, String superclassFqn)
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
                CompilationUnit cu = JdtPullUpField.parse(src, "Unknown.java");
                if (JdtPullUpField.extendsClass(cu, superclassFqn)) {
                    result.add(file);
                }
            }
        }
        return result;
    }
}
