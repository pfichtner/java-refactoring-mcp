package dev.mcp.refactor;

import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

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
     * Renames the local variable or parameter whose name overlaps with
     * {@code offset} in {@code source}, returning the rewritten source.
     *
     * @param source      full source text of the compilation unit
     * @param unitName    file name used for binding resolution (e.g. "Foo.java")
     * @param offset      character offset of any character within the name to rename
     * @param newName     the replacement identifier
     * @return rewritten source text
     */
    public static String renameLocalVariable(String source, String unitName, int offset, String newName) {
        CompilationUnit cu = parse(source, unitName);

        SimpleName target = findSimpleName(cu, offset);
        IBinding binding = target.resolveBinding();

        if (binding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve binding at offset " + offset + " in " + unitName +
                    ". Ensure the source is valid Java.");
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

    private static CompilationUnit parse(String source, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setEnvironment(
                new String[0],  // classpath
                new String[0],  // sourcepath roots
                new String[0],  // encodings
                true            // include running VM bootclasspath
        );
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        return (CompilationUnit) parser.createAST(null);
    }

    private static SimpleName findSimpleName(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at offset " + offset);
        }
        // Walk up to find the enclosing SimpleName
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
