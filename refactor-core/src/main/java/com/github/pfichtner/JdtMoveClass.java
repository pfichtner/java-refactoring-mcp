package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
 *   <li>Fully-qualified type references in source code (e.g.
 *       {@code com.example.service.Calculator calc}) are not updated.</li>
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
     *
     * @param project     Maven project providing source roots and classpath
     * @param sourceFile  absolute path to the {@code .java} file to move
     * @param newPackage  fully-qualified target package, e.g. {@code "com.example.util"}
     * @return {@link Result} describing all required file changes
     */
    public static Result moveClass(
            MavenProject project, Path sourceFile, String newPackage)
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
        if (pkgDecl != null) {
            int pkgStart = pkgDecl.getStartPosition();
            int pkgEnd   = pkgStart + pkgDecl.getLength();
            if (pkgEnd < source.length() && source.charAt(pkgEnd) == '\n') pkgEnd++;
            String replacement = newPackage.isEmpty() ? "" : "package " + newPackage + ";\n";
            newClassSource = source.substring(0, pkgStart) + replacement + source.substring(pkgEnd);
        } else {
            newClassSource = "package " + newPackage + ";\n\n" + source;
        }

        // -------------------------------------------------------------------------
        // 2. Compute new file path
        // -------------------------------------------------------------------------
        Path sourceRoot = findSourceRoot(project, absSource);
        String relPath  = newPackage.replace('.', '/') + "/" + className + ".java";
        Path newFilePath = sourceRoot.resolve(relPath).normalize();

        // -------------------------------------------------------------------------
        // 3. Update explicit imports in all project files
        // -------------------------------------------------------------------------
        Map<Path, String> changedImports = new LinkedHashMap<>();

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
                    for (Object o : fileCu.imports()) {
                        if (o instanceof ImportDeclaration imp
                                && !imp.isOnDemand()
                                && !imp.isStatic()
                                && imp.getName().getFullyQualifiedName().equals(oldFqn)) {
                            Name impName = imp.getName();
                            StringBuilder sb = new StringBuilder(fileSource);
                            sb.replace(impName.getStartPosition(),
                                    impName.getStartPosition() + impName.getLength(),
                                    newFqn);
                            changedImports.put(file, sb.toString());
                            break;
                        }
                    }
                }
            }
        }

        return new Result(newClassSource, newFilePath, changedImports);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Path findSourceRoot(MavenProject project, Path sourceFile)
            throws IOException, InterruptedException {
        for (Path root : project.sourceRoots()) {
            if (sourceFile.startsWith(root.toAbsolutePath().normalize())) return root;
        }
        throw new IllegalArgumentException(
                "Source file is not within any project source root: " + sourceFile);
    }

    private static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        for (Object o : cu.types()) {
            if (o instanceof TypeDeclaration td) return td;
        }
        throw new IllegalArgumentException("No type declaration found in the source.");
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
