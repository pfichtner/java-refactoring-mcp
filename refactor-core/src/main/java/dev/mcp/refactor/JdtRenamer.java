package dev.mcp.refactor;

import dev.mcp.refactor.project.MavenProject;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless semantic rename using JDT ASTParser + ASTRewrite.
 *
 * Uses JDT's binding resolution to identify the exact symbol and all its
 * references — not string matching.
 */
public class JdtRenamer {

    /**
     * Renames the local variable or parameter at {@code offset} within {@code sourceFile},
     * using the project's source roots and dependency classpath for binding resolution.
     *
     * @param project    Maven project providing classpath and source roots
     * @param sourceFile absolute path to the file containing the target symbol
     * @param offset     character offset of any character within the name to rename
     * @param newName    the replacement identifier
     * @return rewritten source text (file is not modified on disk)
     */
    public static String renameLocalVariable(
            MavenProject project, Path sourceFile, int offset, String newName)
            throws IOException, InterruptedException {
        String source = Files.readString(sourceFile);
        String unitName = sourceFile.getFileName().toString();
        String[] classpath = project.classpath();
        String[] sourcePaths = project.sourceRoots().stream()
                .map(Path::toString)
                .toArray(String[]::new);
        return rename(source, unitName, offset, newName, classpath, sourcePaths);
    }

    /**
     * Renames the local variable or parameter at {@code offset} in {@code source}.
     * Uses only the running VM's bootclasspath — suitable for self-contained snippets.
     *
     * @param source   full source text of the compilation unit
     * @param unitName file name used for binding resolution (e.g. "Foo.java")
     * @param offset   character offset of any character within the name to rename
     * @param newName  the replacement identifier
     * @return rewritten source text
     */
    public static String renameLocalVariable(
            String source, String unitName, int offset, String newName) {
        return rename(source, unitName, offset, newName, new String[0], new String[0]);
    }

    private static String rename(
            String source, String unitName, int offset, String newName,
            String[] classpath, String[] sourcePaths) {
        CompilationUnit cu = parse(source, unitName, classpath, sourcePaths);

        SimpleName target = findSimpleName(cu, offset);
        IBinding binding = target.resolveBinding();

        if (binding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve binding at offset " + offset + " in " + unitName
                    + ". Ensure the source is valid Java.");
        }
        if (!(binding instanceof IVariableBinding vb) || (!vb.isParameter() && !isLocal(vb))) {
            throw new IllegalArgumentException(
                    "Element at offset " + offset + " is not a local variable or parameter.");
        }

        String bindingKey = binding.getKey();
        List<SimpleName> occurrences = collectOccurrences(cu, bindingKey);

        ASTRewrite rewrite = ASTRewrite.create(cu.getAST());
        for (SimpleName name : occurrences) {
            rewrite.set(name, SimpleName.IDENTIFIER_PROPERTY, newName, null);
        }

        try {
            Document document = new Document(source);
            TextEdit edits = rewrite.rewriteAST(document, null);
            edits.apply(document);
            return document.get();
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply rename edits", e);
        }
    }

    private static CompilationUnit parse(
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

    private static List<SimpleName> collectOccurrences(CompilationUnit cu, String bindingKey) {
        List<SimpleName> result = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                IBinding b = node.resolveBinding();
                if (b != null && bindingKey.equals(b.getKey())) {
                    result.add(node);
                }
                return true;
            }
        });
        return result;
    }

    private static boolean isLocal(IVariableBinding vb) {
        return !vb.isField() && !vb.isEnumConstant();
    }
}
