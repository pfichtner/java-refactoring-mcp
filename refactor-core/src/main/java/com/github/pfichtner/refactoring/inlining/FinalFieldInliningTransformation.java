package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.isPure;
import static org.eclipse.jdt.core.dom.Modifier.isFinal;
import static org.eclipse.jdt.core.dom.Modifier.isPrivate;
import static org.eclipse.jdt.core.dom.Modifier.isStatic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * Replaces references to {@code private final} instance fields whose
 * declaration initializer is a pure expression with that initializer expression.
 *
 * <p>Only fields declared as {@code private final} (non-static) with a pure
 * initializer in the field declaration are eligible. Fields whose value is set
 * exclusively in a constructor are excluded because their value is not visible
 * from the declaration site.
 *
 * <p>Purity is determined by {@link InliningSupport#isPure}: literals and
 * constant-arithmetic expressions qualify; field reads and method calls do not.
 * Compound initializers are wrapped in a {@link ParenthesizedExpression} to
 * preserve operator precedence at the substitution site.
 */
final class FinalFieldInliningTransformation {

    private FinalFieldInliningTransformation() {}

    /**
     * Returns all inlinable {@code private final} instance-field references in
     * this compilation unit, in reverse AST order (bottom-up, so applying edits
     * in list order does not invalidate earlier offsets).
     */
    static List<Expression> collectCandidates(CompilationUnit cu) {
        Map<String, Expression> fieldInitializers = collectInlinableFields(cu);
        if (fieldInitializers.isEmpty()) return Collections.emptyList();

        List<Expression> candidates = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                if (node.isDeclaration()) return false;
                // The name component of a QualifiedName is handled by its parent.
                if (node.getParent() instanceof QualifiedName qn && qn.getName() == node) return false;
                IBinding binding = node.resolveBinding();
                if (!(binding instanceof IVariableBinding var) || !var.isField()) return false;
                if (fieldInitializers.containsKey(var.getKey())) candidates.add(node);
                return false;
            }

            @Override
            public boolean visit(FieldAccess node) {
                // Only plain this.field access — no qualified this or other receivers.
                if (!(node.getExpression() instanceof ThisExpression te)
                        || te.getQualifier() != null) return false;
                IVariableBinding var = node.resolveFieldBinding();
                if (var == null) return false;
                if (fieldInitializers.containsKey(var.getKey())) candidates.add(node);
                return false;
            }
        });

        Collections.reverse(candidates);
        return candidates;
    }

    /** Returns the simple field name for use in diagnostics. */
    static String fieldName(Expression fieldRef) {
        if (fieldRef instanceof SimpleName sn) return sn.getIdentifier();
        if (fieldRef instanceof FieldAccess fa) return fa.getName().getIdentifier();
        return fieldRef.toString();
    }

    /**
     * Replaces {@code fieldRef} with the declaration initializer of the field it
     * resolves to. Returns {@code null} when the field or its initializer cannot
     * be found, or the initializer is not pure.
     */
    static String tryInline(CompilationUnit cu, String source, Expression fieldRef) {
        IVariableBinding var = resolveFieldBinding(fieldRef);
        if (var == null) return null;

        Map<String, Expression> initializers = collectInlinableFields(cu);
        Expression initializer = initializers.get(var.getKey());
        if (initializer == null) return null;

        AST ast = cu.getAST();
        @SuppressWarnings("unchecked")
        Expression copy = (Expression) ASTNode.copySubtree(ast, initializer);
        Expression replacement = isAtomic(initializer) ? copy : parenthesize(ast, copy);

        ASTRewrite rewrite = ASTRewrite.create(ast);
        rewrite.replace(fieldRef, replacement, null);

        try {
            Document document = new Document(source);
            TextEdit edits = rewrite.rewriteAST(document, null);
            edits.apply(document);
            return document.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply final-field inline edits", e);
        }
    }

    /**
     * Collects the binding key → initializer map for all eligible fields:
     * {@code private final} non-static fields whose declaration initializer is
     * {@linkplain InliningSupport#isPure pure}.
     */
    private static Map<String, Expression> collectInlinableFields(CompilationUnit cu) {
        Map<String, Expression> result = new HashMap<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(FieldDeclaration node) {
                int modifiers = node.getModifiers();
                if (!isFinal(modifiers) || isStatic(modifiers) || !isPrivate(modifiers)) return false;
                for (Object obj : node.fragments()) {
                    if (!(obj instanceof VariableDeclarationFragment frag)) continue;
                    Expression init = frag.getInitializer();
                    if (init == null || !isPure(init)) continue;
                    IVariableBinding binding = frag.resolveBinding();
                    if (binding != null) result.put(binding.getKey(), init);
                }
                return false;
            }
        });
        return result;
    }

    private static IVariableBinding resolveFieldBinding(Expression fieldRef) {
        if (fieldRef instanceof SimpleName sn) {
            IBinding b = sn.resolveBinding();
            return b instanceof IVariableBinding var ? var : null;
        }
        if (fieldRef instanceof FieldAccess fa) return fa.resolveFieldBinding();
        return null;
    }

    /** True when the expression is an atomic node that never requires extra parens. */
    private static boolean isAtomic(Expression expr) {
        return expr instanceof NumberLiteral
                || expr instanceof BooleanLiteral
                || expr instanceof CharacterLiteral
                || expr instanceof StringLiteral
                || expr instanceof NullLiteral
                || expr instanceof SimpleName;
    }

    private static ParenthesizedExpression parenthesize(AST ast, Expression expr) {
        ParenthesizedExpression pe = ast.newParenthesizedExpression();
        pe.setExpression(expr);
        return pe;
    }
}
