package dev.mcp.refactor;

import dev.mcp.refactor.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
 *
 * <p>Known limitation: fully-qualified type references in source code (e.g.
 * {@code com.example.service.Foo foo}) are not updated.
 */
public class JdtRenamePackage {

    /** A file whose source and/or location changed. */
    public record FileChange(Path oldPath, Path newPath, String newSource) {
        /** {@code true} when the file also moves to a new directory. */
        public boolean pathChanged() { return !oldPath.equals(newPath); }
    }

    public record Result(List<FileChange> changedFiles) {}

    /**
     * Renames {@code oldPackage} to {@code newPackage} in the project.
     *
     * @param project    Maven project providing source roots
     * @param oldPackage fully-qualified old package, e.g. {@code com.example.service}
     * @param newPackage fully-qualified new package, e.g. {@code com.example.util}
     */
    public static Result renamePackage(
            MavenProject project, String oldPackage, String newPackage)
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

        return new Result(changes);
    }

    // -------------------------------------------------------------------------
    // Per-file processing
    // -------------------------------------------------------------------------

    static FileChange processFile(
            String source, Path file, Path sourceRoot,
            String oldPackage, String newPackage) {

        String modified = source;
        boolean touched = false;

        // 1. Update package declaration if this file belongs to oldPackage (or a sub-package)
        String declaredPkg = extractDeclaredPackage(source);
        String newDeclaredPkg = remapPackage(declaredPkg, oldPackage, newPackage);
        if (newDeclaredPkg != null) {
            modified = modified.replace(
                    "package " + declaredPkg + ";",
                    "package " + newDeclaredPkg + ";");
            touched = true;
        }

        // 2. Update imports: "import OLD.X" → "import NEW.X"
        //    Trailing period prevents matching "import OLD_longer.X" (oldPackage = "OLD").
        String importOld = "import " + oldPackage + ".";
        String importNew = "import " + newPackage + ".";
        if (modified.contains(importOld)) {
            modified = modified.replace(importOld, importNew);
            touched = true;
        }

        if (!touched) return null;

        // Compute new file path (only changes for files IN the old package)
        Path newPath;
        if (newDeclaredPkg != null) {
            String relPath = newDeclaredPkg.replace('.', '/') + "/" + file.getFileName();
            newPath = sourceRoot.resolve(relPath).normalize();
        } else {
            newPath = file; // path unchanged, only imports updated
        }

        return new FileChange(file, newPath, modified);
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

    static String extractDeclaredPackage(String source) {
        int idx = source.indexOf("package ");
        if (idx < 0) return null;
        int semi = source.indexOf(';', idx);
        if (semi < 0) return null;
        return source.substring(idx + "package ".length(), semi).trim();
    }
}
