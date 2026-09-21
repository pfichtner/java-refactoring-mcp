package com.github.pfichtner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.QualifiedName;

import com.github.pfichtner.project.JavaProject;

/**
 * Headless Rename Package refactoring.
 *
 * <p>Renames a package (and its sub-packages) across a Maven project:
 * <ol>
 *   <li>Updates the {@code package} declaration in every {@code .java} file
 *       whose package is {@code oldPackage} or a sub-package of it.</li>
 *   <li>Computes new canonical file paths for all moved files.</li>
 *   <li>Updates every {@code import oldPackage.Something} (single-class and
 *       wildcard) to {@code import newPackage.Something} throughout the project.
 *       The trailing period used in matching prevents false matches on longer
 *       package names (e.g. {@code com.example.service} will not match
 *       {@code com.example.serviceable}).</li>
 * </ol>
 *
 * <p>The caller is responsible for writing each {@link FileChange#newSource()}
 * to {@link FileChange#newPath()} and deleting the original file.
 */
public class JdtRenamePackage {

    public record Result(List<FileChange> changedFiles) {}

    /**
     * Renames {@code oldPackage} to {@code newPackage} in the project.
     *
     * @param project    Maven project providing source roots
     * @param oldPackage fully-qualified old package, e.g. {@code com.example.service}
     * @param newPackage fully-qualified new package, e.g. {@code com.example.util}
     */
    public static Result renamePackage(
            JavaProject project, String oldPackage, String newPackage)
            throws IOException, InterruptedException {

        if (oldPackage.equals(newPackage)) {
            throw new IllegalArgumentException(
                    "Old and new package names are identical: '" + oldPackage + "'.");
        }

        List<FileChange> changes = new ArrayList<>();

        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.walk(root)) {
                for (Path file : (Iterable<Path>) stream
                        .filter(p -> p.toString().endsWith(".java"))
                        .map(p -> p.toAbsolutePath().normalize())
                        ::iterator) {
                    String source = Files.readString(file);
                    FileChange change = processFile(source, file, root, oldPackage, newPackage);
                    if (change != null) changes.add(change);
                }
            }
        }

        if (changes.isEmpty()) {
            throw new IllegalArgumentException(
                    "No files found in package '" + oldPackage + "' or importing from it.");
        }

        changes.sort(Comparator.comparing(fc -> fc.oldPath().toString()));
        return new Result(changes);
    }

    // -------------------------------------------------------------------------
    // Per-file processing
    // -------------------------------------------------------------------------

    static FileChange processFile(
            String source, Path file, Path sourceRoot,
            String oldPackage, String newPackage) {

        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        String fqnPrefix = oldPackage + ".";

        record Edit(int start, int end, String replacement) {}
        List<Edit> edits = new ArrayList<>();

        // 1. Package declaration
        PackageDeclaration pkgDecl = cu.getPackage();
        String declaredPkg = pkgDecl != null ? pkgDecl.getName().getFullyQualifiedName() : null;
        String newDeclaredPkg = remapPackage(declaredPkg, oldPackage, newPackage);
        if (newDeclaredPkg != null) {
            int start = pkgDecl.getStartPosition();
            edits.add(new Edit(start, start + pkgDecl.getLength(),
                    "package " + newDeclaredPkg + ";"));
        }

        // 2. Import declarations: replace the package prefix in matching import names
        ((List<?>) cu.imports()).stream()
                .filter(o -> o instanceof ImportDeclaration)
                .map(o -> (ImportDeclaration) o)
                .filter(imp -> {
                    String importName = imp.getName().getFullyQualifiedName();
                    return importName.equals(oldPackage) || importName.startsWith(fqnPrefix);
                })
                .forEach(imp -> {
                    int nameStart = imp.getName().getStartPosition();
                    edits.add(new Edit(nameStart, nameStart + oldPackage.length(), newPackage));
                });

        // 3. Fully-qualified code references (QualifiedName nodes outside package/import
        //    declarations), e.g. in field types, local variables, lambdas.
        cu.accept(new ASTVisitor(true) {
            int skipDepth = 0;
            @Override public boolean visit(PackageDeclaration n) { skipDepth++; return true; }
            @Override public void endVisit(PackageDeclaration n) { skipDepth--; }
            @Override public boolean visit(ImportDeclaration n)  { skipDepth++; return true; }
            @Override public void endVisit(ImportDeclaration n)  { skipDepth--; }

            @Override
            public boolean visit(QualifiedName node) {
                if (skipDepth == 0 && node.getFullyQualifiedName().startsWith(fqnPrefix)) {
                    edits.add(new Edit(node.getStartPosition(),
                            node.getStartPosition() + oldPackage.length(), newPackage));
                    // returning false avoids visiting nested qualifier nodes that would
                    // generate duplicate edits at the same start position
                    return false;
                }
                return true;
            }
        });

        if (edits.isEmpty()) return null;

        // Apply all edits in reverse position order to preserve offsets
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        StringBuilder sb = new StringBuilder(source);
        for (Edit e : edits) {
            sb.replace(e.start(), e.end(), e.replacement());
        }

        // Compute new file path (only changes for files IN the old package)
        Path newPath;
        if (newDeclaredPkg != null) {
            String relPath = newDeclaredPkg.replace('.', '/') + "/" + file.getFileName();
            newPath = sourceRoot.resolve(relPath).normalize();
        } else {
            newPath = file; // path unchanged, only imports updated
        }

        return new FileChange(file, newPath, sb.toString());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    static String remapPackage(String pkg, String oldPackage, String newPackage) {
        if (pkg == null) return null;
        if (pkg.equals(oldPackage)) return newPackage;
        if (pkg.startsWith(oldPackage + ".")) return newPackage + pkg.substring(oldPackage.length());
        return null;
    }
}
