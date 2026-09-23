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
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Headless Move Class refactoring using JDT ASTParser.
 *
 * <p>Moves a Java class to a new package by:
 * <ol>
 *   <li>Updating the {@code package} declaration in the class source.</li>
 *   <li>Computing the new canonical file path within the project source root.</li>
 *   <li>Updating all explicit single-class {@code import} statements in the
 *       project that reference the moved class.</li>
 * </ol>
 *
 * <p>The caller is responsible for writing {@link Result#newClassSource()} to
 * {@link Result#newFilePath()} and deleting the original file.
 *
 * <p>Known limitations (documented, not rejected):
 * <ul>
 *   <li>Same-package usages without explicit imports are NOT updated — classes
 *       in the original package that reference the moved class by simple name
 *       will require manual import additions.</li>
 *   <li>Wildcard imports ({@code import com.example.*;}) are not updated but
 *       will require no change on the consumer side if the wildcard now points
 *       to the new package.</li>
 * </ul>
 */
public class JdtMoveClass {

    /**
     * Result of a move-class operation.
     *
     * @param newClassSource  class source with the updated {@code package} declaration
     * @param newFilePath     absolute canonical path where the file should be written
     * @param changedImports  map of {@code path → new source} for every project file
     *                        whose import of the moved class was updated
     */
    public record Result(
            String newClassSource,
            Path newFilePath,
            Map<Path, String> changedImports) {}

    /**
     * Moves {@code sourceFile} to {@code newPackage} within the project.
     * Equivalent to {@link #moveClass(JavaProject, Path, String, boolean)} with {@code widenVisibility=true}.
     */
    public static Result moveClass(
            JavaProject project, Path sourceFile, String newPackage)
            throws IOException, InterruptedException {
        return moveClass(project, sourceFile, newPackage, true);
    }

    /**
     * Moves {@code sourceFile} to {@code newPackage} within the project.
     *
     * @param project         Maven project providing source roots and classpath
     * @param sourceFile      absolute path to the {@code .java} file to move
     * @param newPackage      fully-qualified target package, e.g. {@code "com.example.util"}
     * @param widenVisibility if {@code true} and the class has no access modifier (package-private),
     *                        {@code public} is added to the class declaration
     * @return {@link Result} describing all required file changes
     */
    public static Result moveClass(
            JavaProject project, Path sourceFile, String newPackage, boolean widenVisibility)
            throws IOException, InterruptedException {

        Path absSource = sourceFile.toAbsolutePath().normalize();
        String source  = Files.readString(absSource);

        CompilationUnit cu = parse(source, absSource.getFileName().toString());

        // Determine class name and old package
        TypeDeclaration primaryType = findPrimaryType(cu);
        String className = primaryType.getName().getIdentifier();

        PackageDeclaration pkgDecl = cu.getPackage();
        String oldPackage = pkgDecl != null ? pkgDecl.getName().getFullyQualifiedName() : "";

        if (oldPackage.equals(newPackage)) {
            throw new IllegalArgumentException(
                    "'" + className + "' is already in package '" + newPackage + "'.");
        }

        String oldFqn = oldPackage.isEmpty() ? className : oldPackage + "." + className;
        String newFqn = newPackage.isEmpty() ? className : newPackage + "." + className;

        // -------------------------------------------------------------------------
        // 1. Update package declaration in class source
        // -------------------------------------------------------------------------
        String newClassSource;
        int typeInsertionPoint; // where "public " should be inserted in newClassSource if needed
        if (pkgDecl != null) {
            int pkgStart = pkgDecl.getStartPosition();
            int pkgEnd   = pkgStart + pkgDecl.getLength();
            if (pkgEnd < source.length() && source.charAt(pkgEnd) == '\n') pkgEnd++;
            String replacement = newPackage.isEmpty() ? "" : "package " + newPackage + ";\n";
            newClassSource = source.substring(0, pkgStart) + replacement + source.substring(pkgEnd);
            // Adjust the type declaration position by the package text delta
            int delta = replacement.length() - (pkgEnd - pkgStart);
            typeInsertionPoint = primaryType.getStartPosition() + delta;
        } else {
            String prefix = "package " + newPackage + ";\n\n";
            newClassSource = prefix + source;
            typeInsertionPoint = primaryType.getStartPosition() + prefix.length();
        }

        // -------------------------------------------------------------------------
        // 1a. Widen class visibility to public if package-private and requested
        // -------------------------------------------------------------------------
        if (widenVisibility && primaryType.modifiers().stream()
                .noneMatch(o -> o instanceof Modifier m && (m.isPublic() || m.isProtected() || m.isPrivate()))) {
            newClassSource = newClassSource.substring(0, typeInsertionPoint)
                    + "public "
                    + newClassSource.substring(typeInsertionPoint);
        }

        // -------------------------------------------------------------------------
        // 2. Compute new file path
        // -------------------------------------------------------------------------
        Path sourceRoot = findSourceRoot(project, absSource);
        String relPath  = newPackage.replace('.', '/') + "/" + className + ".java";
        Path newFilePath = sourceRoot.resolve(relPath).normalize();

        // -------------------------------------------------------------------------
        // 3. Update imports and FQN code references in all project files
        // -------------------------------------------------------------------------
        Map<Path, String> changedImports = new LinkedHashMap<>();

        record Edit(int start, int end, String replacement) {}

        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.walk(root)) {
                for (Path file : (Iterable<Path>) stream
                        .filter(p -> p.toString().endsWith(".java"))
                        .map(p -> p.toAbsolutePath().normalize())
                        ::iterator) {
                    if (file.equals(absSource)) continue; // skip the moved file itself
                    String fileSource = Files.readString(file);
                    CompilationUnit fileCu = parse(fileSource, file.getFileName().toString());
                    List<Edit> edits = new ArrayList<>();

                    // Update explicit single-class import
                    ((List<?>) fileCu.imports()).stream()
                            .filter(o -> o instanceof ImportDeclaration imp
                                    && !imp.isOnDemand()
                                    && !imp.isStatic()
                                    && imp.getName().getFullyQualifiedName().equals(oldFqn))
                            .map(o -> {
                                Name n = ((ImportDeclaration) o).getName();
                                return new Edit(n.getStartPosition(), n.getStartPosition() + n.getLength(), newFqn);
                            })
                            .forEach(edits::add);

                    // Update fully-qualified code references (QualifiedName nodes in body)
                    fileCu.accept(new ASTVisitor(true) {
                        int skipDepth = 0;
                        @Override public boolean visit(PackageDeclaration n) { skipDepth++; return true; }
                        @Override public void endVisit(PackageDeclaration n) { skipDepth--; }
                        @Override public boolean visit(ImportDeclaration n)  { skipDepth++; return true; }
                        @Override public void endVisit(ImportDeclaration n)  { skipDepth--; }

                        @Override
                        public boolean visit(QualifiedName node) {
                            if (skipDepth == 0 && node.getFullyQualifiedName().equals(oldFqn)) {
                                edits.add(new Edit(node.getStartPosition(),
                                        node.getStartPosition() + node.getLength(), newFqn));
                                return false; // avoid duplicate edits on nested qualifier nodes
                            }
                            return true;
                        }
                    });

                    if (!edits.isEmpty()) {
                        edits.sort(Comparator.comparingInt(Edit::start).reversed());
                        StringBuilder sb = new StringBuilder(fileSource);
                        for (Edit e : edits) sb.replace(e.start(), e.end(), e.replacement());
                        changedImports.put(file, sb.toString());
                    }
                }
            }
        }

        return new Result(newClassSource, newFilePath, changedImports);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Path findSourceRoot(JavaProject project, Path sourceFile)
            throws IOException, InterruptedException {
        for (Path root : project.sourceRoots()) {
            if (sourceFile.startsWith(root.toAbsolutePath().normalize())) return root;
        }
        throw new IllegalArgumentException(
                "Source file is not within any project source root: " + sourceFile);
    }

    private static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        return ((List<?>) cu.types()).stream()
                .filter(o -> o instanceof TypeDeclaration)
                .map(o -> (TypeDeclaration) o)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No type declaration found in the source."));
    }

    private static CompilationUnit parse(String source, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(false); // not needed for move
        return (CompilationUnit) parser.createAST(null);
    }
}
