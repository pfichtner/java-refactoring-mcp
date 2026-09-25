package com.github.pfichtner.refactoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MemberRef;
import org.eclipse.jdt.core.dom.MethodRef;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Headless semantic rename using JDT ASTParser + ASTRewrite.
 *
 * Uses JDT's binding resolution to identify the exact symbol and all its
 * references across files — not string matching.
 */
public class JdtRenamer {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Renames the Java element at {@code offset} in {@code sourceFile} across
     * all source files in the project. Handles local variables, parameters,
     * fields, methods, and types.
     *
     * <p>For local variables and parameters the rename is confined to the
     * declaring method (single file). For fields, methods, and types all
     * project source files are scanned.
     *
     * @param project    Maven project providing source roots and classpath
     * @param sourceFile file containing the target symbol (must be inside the project)
     * @param offset     character offset of any character within the name to rename
     * @param newName    the replacement identifier
     * @return list of {@link FileChange} for every changed file;
     *         paths are absolute and normalised; when a public type is renamed
     *         the entry's {@code newPath} reflects the new filename
     * @throws IllegalArgumentException if the binding cannot be resolved or the
     *                                  element type is not supported
     */
    public static List<FileChange> rename(
            JavaProject project, Path sourceFile, int offset, String newName)
            throws IOException, InterruptedException {

        String[] classpath = project.classpath();
        String[] sourcePaths = project.sourceRoots().stream()
                .map(Path::toString).toArray(String[]::new);

        List<Path> allFiles = JdtProjectSources.collectSourceFiles(project);
        Map<Path, String> sources = JdtProjectSources.readAll(allFiles);
        Map<Path, CompilationUnit> cus = JdtProjectSources.parseAll(allFiles, classpath, sourcePaths);

        Path absTarget = sourceFile.toAbsolutePath().normalize();
        CompilationUnit targetCu = cus.get(absTarget);
        if (targetCu == null) {
            throw new IllegalArgumentException(
                    "Source file not found in project: " + sourceFile);
        }

        SimpleName targetName = findSimpleName(targetCu, offset);
        IBinding binding = targetName.resolveBinding();
        if (binding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve binding at offset " + offset
                    + " in " + sourceFile.getFileName()
                    + ". Ensure the source is valid Java.");
        }

        validateRenameTarget(binding, offset);

        List<FileChange> changed = new ArrayList<>();
        for (Map.Entry<Path, CompilationUnit> entry : cus.entrySet()) {
            Path filePath = entry.getKey();
            CompilationUnit cu = entry.getValue();

            List<SimpleName> occurrences = collectOccurrences(cu, binding);
            if (occurrences.isEmpty()) continue;

            ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
            for (SimpleName name : occurrences) {
                rewrite.set(name, SimpleName.IDENTIFIER_PROPERTY, newName, null);
            }
            try {
                Document doc = new Document(sources.get(filePath));
                TextEdit edits = rewrite.rewriteAST(doc, null);
                edits.apply(doc);
                Path newPath = computeNewPath(binding, targetName, filePath, absTarget, newName);
                changed.add(new FileChange(filePath, newPath, doc.get()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to apply rename in " + filePath, e);
            }
        }
        return changed;
    }

    /**
     * Renames the local variable or parameter at {@code offset} within
     * {@code sourceFile}, using the project's source roots and classpath.
     * Returns the rewritten source (file is not modified on disk).
     */
    public static String renameLocalVariable(
            JavaProject project, Path sourceFile, int offset, String newName)
            throws IOException, InterruptedException {
        List<FileChange> changed = rename(project, sourceFile, offset, newName);
        Path abs = sourceFile.toAbsolutePath().normalize();
        return changed.stream()
                .filter(fc -> fc.oldPath().equals(abs))
                .map(FileChange::newSource)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Rename produced no change in " + sourceFile.getFileName()));
    }

    private static Path computeNewPath(
            IBinding binding, SimpleName targetName, Path filePath, Path absTarget, String newName) {
        if (binding instanceof ITypeBinding && filePath.equals(absTarget)) {
            String oldFileName = filePath.getFileName().toString();
            if (oldFileName.equals(targetName.getIdentifier() + ".java")) {
                return filePath.resolveSibling(newName + ".java");
            }
        }
        return filePath;
    }

    /**
     * Renames the local variable or parameter at {@code offset} in the given
     * source snippet. Uses only the running VM's bootclasspath — suitable for
     * self-contained snippets without a project context.
     */
    public static String renameLocalVariable(
            String source, String unitName, int offset, String newName) {
        CompilationUnit cu = parseSingle(source, unitName, new String[0], new String[0]);

        SimpleName target = findSimpleName(cu, offset);
        IBinding binding = target.resolveBinding();
        if (binding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve binding at offset " + offset + " in " + unitName
                    + ". Ensure the source is valid Java.");
        }
        if (!(binding instanceof IVariableBinding vb)
                || (!vb.isParameter() && !isLocal(vb))) {
            throw new IllegalArgumentException(
                    "Element at offset " + offset + " is not a local variable or parameter.");
        }

        List<SimpleName> occurrences = collectOccurrencesByKey(cu, binding.getKey());
        ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
        for (SimpleName name : occurrences) {
            rewrite.set(name, SimpleName.IDENTIFIER_PROPERTY, newName, null);
        }
        try {
            Document doc = new Document(source);
            TextEdit edits = rewrite.rewriteAST(doc, null);
            edits.apply(doc);
            return doc.get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply rename edits", e);
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private static void validateRenameTarget(IBinding binding, int offset) {
        if (binding instanceof IVariableBinding) {
            return;
        }
        if (binding instanceof IMethodBinding mb) {
            if (mb.isConstructor()) {
                throw new IllegalArgumentException(
                        "Target at offset " + offset + " is a constructor. "
                        + "Rename the type instead.");
            }
            return; // method rename
        }
        if (binding instanceof ITypeBinding) {
            return; // type rename
        }
        throw new IllegalArgumentException(
                "Element at offset " + offset + " cannot be renamed "
                + "(binding type: " + binding.getClass().getSimpleName() + ").");
    }

    // -------------------------------------------------------------------------
    // Occurrence collection
    // -------------------------------------------------------------------------

    /**
     * Collects all {@link SimpleName} nodes in {@code cu} that refer to the
     * same element as {@code target}.
     *
     * <p>For type bindings, constructor declaration names are also included so
     * that renaming a type also renames its constructors.
     */
    private static List<SimpleName> collectOccurrences(CompilationUnit cu, IBinding target) {
        String key = normalizedKey(target);
        List<SimpleName> result = new ArrayList<>();
        cu.accept(new ASTVisitor(true) {
            @Override
            public boolean visit(SimpleName node) {
                addIfMatch(node);
                return true;
            }
            @Override
            public boolean visit(MemberRef node) {  // {@link Type#field}
                addIfMatch(node.getName());
                if (node.getQualifier() instanceof SimpleName sn) addIfMatch(sn);
                return false; // don't re-visit children via visit(SimpleName)
            }
            @Override
            public boolean visit(MethodRef node) {  // {@link Type#method()}
                addIfMatch(node.getName());
                if (node.getQualifier() instanceof SimpleName sn) addIfMatch(sn);
                return false; // don't re-visit children via visit(SimpleName)
            }
            @Override
            public boolean visit(ImportDeclaration node) {
                // Single static imports (import static X.member;) often resolve to
                // null bindings for the imported name; rename them via the
                // qualifying type + segment identifier so removing the old name
                // does not leave a dangling import behind.
                if (node.isStatic() && !node.isOnDemand()) {
                    addIfImportMatches(node.getName());
                }
                return false; // handled here; skip nested SimpleName visits
            }
            void addIfImportMatches(Name name) {
                SimpleName last = (name instanceof SimpleName sn) ? sn
                        : ((QualifiedName) name).getName();
                IBinding b = name.resolveBinding();
                if (b != null && key.equals(normalizedKey(b))) {
                    result.add(last);
                    return;
                }
                // Fall back to the qualifying type + segment identifier. Needed
                // when the imported name has no binding at all, or resolves to a
                // sibling overload of the renamed method (a single-static import
                // names the whole overload group).
                ITypeBinding declaring = declaringTypeOf(target);
                ITypeBinding imported = name instanceof QualifiedName qn
                        && qn.getQualifier().resolveBinding() instanceof ITypeBinding tb
                                ? tb : null;
                if (declaring != null && imported != null
                        && declaring.getTypeDeclaration().getKey()
                                .equals(imported.getTypeDeclaration().getKey())
                        && last.getIdentifier().equals(target.getName())) {
                    result.add(last);
                }
            }
            void addIfMatch(SimpleName node) {
                IBinding b = node.resolveBinding();
                if (b == null) return;
                if (key.equals(normalizedKey(b))) {
                    result.add(node);
                } else if (target instanceof ITypeBinding
                        && b instanceof IMethodBinding mb && mb.isConstructor()) {
                    // Also rename constructor declaration names when renaming the type
                    ITypeBinding declaring = mb.getDeclaringClass();
                    if (declaring != null && key.equals(normalizedKey(declaring))) {
                        result.add(node);
                    }
                }
            }
        });
        return result;
    }

    /** Collects by exact binding key — used for the single-file snippet path. */
    private static List<SimpleName> collectOccurrencesByKey(CompilationUnit cu, String bindingKey) {
        List<SimpleName> result = new ArrayList<>();
        cu.accept(new ASTVisitor(true) {
            @Override
            public boolean visit(SimpleName node) {
                IBinding b = node.resolveBinding();
                if (b != null && bindingKey.equals(b.getKey())) result.add(node);
                return true;
            }
        });
        return result;
    }

    /**
     * Normalises the binding key for comparison:
     * <ul>
     *   <li>Methods: uses the method <em>declaration</em> key (strips type-argument erasure)</li>
     *   <li>Types: uses the type <em>declaration</em> key (strips parameterisation)</li>
     *   <li>Variables: exact key</li>
     * </ul>
     */
    private static String normalizedKey(IBinding binding) {
        return switch (binding) {
            case IMethodBinding mb -> mb.getMethodDeclaration().getKey();
            case ITypeBinding tb   -> tb.getTypeDeclaration().getKey();
            default                -> binding.getKey();
        };
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    /** Parses a single source string — for the snippet-based API. */
    private static CompilationUnit parseSingle(
            String source, String unitName, String[] classpath, String[] sourcePaths) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setEnvironment(classpath, sourcePaths, null, true);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        return (CompilationUnit) parser.createAST(null);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static SimpleName findSimpleName(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at offset " + offset);
        }
        while (node != null && !(node instanceof SimpleName)) {
            node = node.getParent();
        }
        if (!(node instanceof SimpleName sn)) {
            throw new IllegalArgumentException("No SimpleName found at offset " + offset);
        }
        return sn;
    }

    private static boolean isLocal(IVariableBinding vb) {
        return !vb.isField() && !vb.isEnumConstant();
    }

    private static ITypeBinding declaringTypeOf(IBinding binding) {
        if (binding instanceof IMethodBinding mb) return mb.getDeclaringClass();
        if (binding instanceof IVariableBinding vb) return vb.getDeclaringClass();
        if (binding instanceof ITypeBinding tb) return tb;
        return null;
    }

    /**
     * Converts a 1-based line and column to a 0-based character offset in {@code source}.
     * Used by the CLI and MCP layer so the engine only deals with offsets.
     */
    public static int toOffset(String source, int line, int col) {
        int pos = 0;
        for (int l = 1; l < line; l++) {
            int nl = source.indexOf('\n', pos);
            if (nl < 0) {
                throw new IllegalArgumentException(
                        "Line " + line + " is out of range (file has fewer lines).");
            }
            pos = nl + 1;
        }
        int offset = pos + col - 1;
        if (offset > source.length()) {
            throw new IllegalArgumentException(
                    "Column " + col + " is out of range on line " + line + ".");
        }
        return offset;
    }
}
