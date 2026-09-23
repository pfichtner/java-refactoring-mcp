package com.github.pfichtner.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

/**
 * Headless Inline Variable refactoring using JDT ASTParser.
 *
 * <p>Replaces every use of a local variable with its initializer expression,
 * then removes the declaration statement.
 *
 * <p>Parenthesises the inlined expression when the operator context requires it
 * (e.g. inlining {@code a + b} into {@code x * sum} → {@code x * (a + b)}).
 *
 * <p>Precondition failures (structured diagnostic, no file modified):
 * <ul>
 *   <li>Target is not a local variable (fields, parameters rejected)</li>
 *   <li>Declaration has multiple fragments ({@code int x = 1, y = 2})</li>
 *   <li>Variable has no initializer ({@code int x; x = 5;})</li>
 * </ul>
 */
public class JdtInliner {

    /**
     * Inlines the local variable at {@code offset} and returns the rewritten source.
     *
     * @param source   full source text
     * @param unitName file name for binding resolution (e.g. {@code "Foo.java"})
     * @param offset   character offset of any character within the variable name
     * @return rewritten source with all uses replaced and declaration removed
     * @throws IllegalArgumentException if the inline cannot be performed safely
     */
    public static String inlineVariable(String source, String unitName, int offset) {
        CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(source, unitName);

        SimpleName target = findSimpleName(cu, offset);
        IBinding binding = target.resolveBinding();

        if (!(binding instanceof IVariableBinding vb)) {
            throw new IllegalArgumentException(
                    "No variable found at offset " + offset + ".");
        }
        if (vb.isField() || vb.isEnumConstant()) {
            throw new IllegalArgumentException(
                    "Cannot inline field '" + vb.getName() + "'. Only local variables are supported.");
        }
        if (vb.isParameter()) {
            throw new IllegalArgumentException(
                    "Cannot inline parameter '" + vb.getName() + "'.");
        }

        String bindingKey = vb.getKey();

        // Find the declaration
        VariableDeclarationStatement decl = findDeclaration(cu, bindingKey);
        if (decl == null) {
            throw new IllegalArgumentException(
                    "Cannot find the declaration of '" + vb.getName() + "' in this compilation unit.");
        }
        if (decl.fragments().size() > 1) {
            throw new IllegalArgumentException(
                    "Cannot inline '" + vb.getName()
                    + "': declaration contains multiple variables. Split it first.");
        }

        VariableDeclarationFragment fragment = (VariableDeclarationFragment) decl.fragments().get(0);
        Expression init = fragment.getInitializer();
        if (init == null) {
            throw new IllegalArgumentException(
                    "Cannot inline '" + vb.getName() + "': variable has no initializer.");
        }

        // Extract initializer text, parenthesising if necessary
        String initText = source.substring(
                init.getStartPosition(), init.getStartPosition() + init.getLength());
        String safeInit = needsParens(init) ? "(" + initText + ")" : initText;

        // Collect all uses (every SimpleName with the same binding key, except the declaration name)
        List<SimpleName> uses = collectUses(cu, bindingKey, fragment.getName());

        // Find declaration line boundaries (in the original source)
        int declStart = decl.getStartPosition();
        int lineStart = declStart;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = declStart + decl.getLength();
        while (lineEnd < source.length() && source.charAt(lineEnd) != '\n') lineEnd++;
        if (lineEnd < source.length()) lineEnd++; // include the newline

        // Sort uses largest-to-smallest so each replacement doesn't shift earlier positions
        uses.sort((a, b) -> b.getStartPosition() - a.getStartPosition());

        // Apply use replacements from end to start
        StringBuilder sb = new StringBuilder(source);
        for (SimpleName use : uses) {
            sb.replace(use.getStartPosition(), use.getStartPosition() + use.getLength(), safeInit);
        }

        // Remove the declaration line (positions still valid: declaration precedes all uses)
        sb.delete(lineStart, lineEnd);

        return sb.toString();
    }

    /**
     * Inlines a {@code static final} constant at {@code offset} and returns the rewritten source.
     *
     * @param source            full source text
     * @param unitName          file name for binding resolution (e.g. {@code "Foo.java"})
     * @param offset            character offset of any character within the constant name (use or declaration)
     * @param allOccurrences    if {@code true}, every reference in the file is replaced;
     *                          if {@code false}, only the single reference at {@code offset} is replaced
     * @param removeDeclaration if {@code true}, the {@code FieldDeclaration} is also deleted
     *                          (requires {@code allOccurrences = true})
     * @return rewritten source
     * @throws IllegalArgumentException if the inline cannot be performed safely
     */
    public static String inlineConstant(String source, String unitName, int offset,
                                        boolean allOccurrences, boolean removeDeclaration) {
        if (removeDeclaration && !allOccurrences) {
            throw new IllegalArgumentException(
                    "removeDeclaration requires allOccurrences=true — "
                    + "cannot remove declaration when only one occurrence is inlined.");
        }

        CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(source, unitName);
        SimpleName target = findSimpleName(cu, offset);

        IBinding binding = target.resolveBinding();
        if (!(binding instanceof IVariableBinding vb)) {
            throw new IllegalArgumentException("No variable found at offset " + offset + ".");
        }
        if (!vb.isField() || vb.isEnumConstant()) {
            throw new IllegalArgumentException(
                    "'" + vb.getName() + "' is not a field. Use inline_variable for local variables.");
        }
        int mods = vb.getModifiers();
        if (!Modifier.isStatic(mods) || !Modifier.isFinal(mods)) {
            throw new IllegalArgumentException(
                    "Cannot inline field '" + vb.getName()
                    + "': only static final constants are supported.");
        }

        String bindingKey = vb.getKey();

        FieldDeclaration fieldDecl = findFieldDeclaration(cu, bindingKey);
        if (fieldDecl == null) {
            throw new IllegalArgumentException(
                    "Constant '" + vb.getName() + "' is not declared in this file.");
        }
        @SuppressWarnings("unchecked") List<VariableDeclarationFragment> fragments =
                fieldDecl.fragments();
        if (fragments.size() > 1) {
            throw new IllegalArgumentException(
                    "Cannot inline '" + vb.getName()
                    + "': declaration contains multiple constants. Split it first.");
        }

        VariableDeclarationFragment fragment = fragments.get(0);
        Expression init = fragment.getInitializer();
        if (init == null) {
            throw new IllegalArgumentException(
                    "Cannot inline '" + vb.getName() + "': constant has no initializer.");
        }

        SimpleName declName = fragment.getName();
        if (target == declName && !allOccurrences) {
            throw new IllegalArgumentException(
                    "Offset points to the constant declaration; "
                    + "invoke on a use, or use allOccurrences=true.");
        }

        String initText = source.substring(
                init.getStartPosition(), init.getStartPosition() + init.getLength());
        String safeInit = needsParens(init) ? "(" + initText + ")" : initText;

        List<Edit> edits = new ArrayList<>();

        if (allOccurrences) {
            for (SimpleName use : collectUses(cu, bindingKey, declName)) {
                edits.add(new Edit(use.getStartPosition(),
                        use.getStartPosition() + use.getLength(), safeInit));
            }
        } else {
            edits.add(new Edit(target.getStartPosition(),
                    target.getStartPosition() + target.getLength(), safeInit));
        }

        if (removeDeclaration) {
            int declStart = fieldDecl.getStartPosition();
            int lineStart = declStart;
            while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
            int lineEnd = declStart + fieldDecl.getLength();
            while (lineEnd < source.length() && source.charAt(lineEnd) != '\n') lineEnd++;
            if (lineEnd < source.length()) lineEnd++;
            edits.add(new Edit(lineStart, lineEnd, ""));
        }

        edits.sort((a, b) -> b.start() - a.start());
        StringBuilder sb = new StringBuilder(source);
        for (Edit ed : edits) sb.replace(ed.start(), ed.end(), ed.replacement());
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private record Edit(int start, int end, String replacement) {}

    private static List<SimpleName> collectUses(
            CompilationUnit cu, String bindingKey, SimpleName declarationName) {
        List<SimpleName> result = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                if (node == declarationName) return true; // skip the declaration itself
                IBinding b = node.resolveBinding();
                if (b != null && bindingKey.equals(b.getKey())) {
                    result.add(node);
                }
                return true;
            }
        });
        return result;
    }

    private static VariableDeclarationStatement findDeclaration(
            CompilationUnit cu, String bindingKey) {
        VariableDeclarationStatement[] found = {null};
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                for (Object o : node.fragments()) {
                    VariableDeclarationFragment f = (VariableDeclarationFragment) o;
                    IVariableBinding b = f.resolveBinding();
                    if (b != null && bindingKey.equals(b.getKey())) {
                        found[0] = node;
                        return false;
                    }
                }
                return found[0] == null;
            }
        });
        return found[0];
    }

    private static FieldDeclaration findFieldDeclaration(CompilationUnit cu, String bindingKey) {
        FieldDeclaration[] found = {null};
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(FieldDeclaration node) {
                for (Object o : node.fragments()) {
                    VariableDeclarationFragment f = (VariableDeclarationFragment) o;
                    IVariableBinding b = f.resolveBinding();
                    if (b != null && bindingKey.equals(b.getKey())) {
                        found[0] = node;
                        return false;
                    }
                }
                return found[0] == null;
            }
        });
        return found[0];
    }

    /**
     * Returns {@code true} when the expression needs to be wrapped in parentheses
     * to preserve semantics when inlined into an unknown operator context.
     * Conservative: parenthesises any compound expression.
     */
    private static boolean needsParens(Expression expr) {
        return !(expr instanceof SimpleName
                || expr instanceof QualifiedName
                || expr instanceof NumberLiteral
                || expr instanceof StringLiteral
                || expr instanceof BooleanLiteral
                || expr instanceof CharacterLiteral
                || expr instanceof NullLiteral
                || expr instanceof MethodInvocation
                || expr instanceof ClassInstanceCreation
                || expr instanceof ArrayAccess
                || expr instanceof FieldAccess
                || expr instanceof SuperFieldAccess
                || expr instanceof SuperMethodInvocation
                || expr instanceof ThisExpression
                || expr instanceof ParenthesizedExpression);
    }

    private static SimpleName findSimpleName(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) throw new IllegalArgumentException("No AST node at offset " + offset);
        while (node != null && !(node instanceof SimpleName)) node = node.getParent();
        if (!(node instanceof SimpleName sn)) {
            throw new IllegalArgumentException("No name found at offset " + offset);
        }
        return sn;
    }
}
