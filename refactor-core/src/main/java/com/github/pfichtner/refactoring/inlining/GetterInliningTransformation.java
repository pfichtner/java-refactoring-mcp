package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.enclosingMethod;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.isGetterBody;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.isPure;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.simpleNames;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.thisExpressions;
import static org.eclipse.jdt.core.dom.Modifier.isStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * Inlines a getter-like method call by direct expression substitution.
 *
 * <p>A getter-like method has a body of exactly one {@code return} statement whose
 * expression is side-effect-free. Such a method can be inlined at <em>any</em> call
 * position — including nested expressions — by replacing the {@link MethodInvocation}
 * node with a copy of the return expression, without any hoisting.
 *
 * <p>Falls back to {@code null} when the method is not getter-like or when any
 * argument/receiver is not pure; the caller should then try the hoisting
 * transformation.
 */
final class GetterInliningTransformation {

    private GetterInliningTransformation() {}

    /**
     * Attempts a direct-substitution inline. Returns the rewritten source, or
     * {@code null} when the method does not qualify for getter inlining.
     */
    static String tryApply(
            CompilationUnit cu, String source,
            MethodInvocation call, MethodDeclaration decl, IMethodBinding binding) {

        if (!isGetterBody(decl)) return null;

        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> params = decl.parameters();
        @SuppressWarnings("unchecked")
        List<Expression> args = call.arguments();
        if (params.size() != args.size()) return null;

        for (Expression arg : args) {
            if (!isPure(arg)) return null;
        }

        Expression receiver = call.getExpression();
        boolean staticMethod = isStatic(binding.getModifiers());
        boolean explicitReceiver = !staticMethod && receiver != null
                && !(receiver instanceof ThisExpression);
        if (explicitReceiver && !isPure(receiver)) return null;

        @SuppressWarnings("unchecked")
        ReturnStatement ret = (ReturnStatement) decl.getBody().statements().get(0);
        Expression returnExpr = ret.getExpression();

        AST ast = cu.getAST();
        Expression substituted = (Expression) ASTNode.copySubtree(ast, returnExpr);
        substituteInExpression(ast, returnExpr, substituted, params, args,
                explicitReceiver ? receiver : null);

        ASTRewrite rewrite = ASTRewrite.create(ast);
        rewrite.replace(call, substituted, null);

        IMethodBinding callerBinding = enclosingMethod(call) != null
                ? enclosingMethod(call).resolveBinding() : null;
        ITypeBinding callerType = callerBinding != null ? callerBinding.getDeclaringClass() : null;
        InlineImports.create(cu, decl, callerType).insertInto(rewrite, cu);

        return applyRewrite(cu, source, rewrite);
    }

    // -------------------------------------------------------------------------
    // Substitution
    // -------------------------------------------------------------------------

    private static void substituteInExpression(
            AST ast,
            Expression originalExpr,
            Expression copiedExpr,
            List<SingleVariableDeclaration> params,
            List<Expression> args,
            Expression receiverExpr) {

        Map<String, Expression> paramMap = new HashMap<>();
        for (int i = 0; i < params.size(); i++) {
            IVariableBinding pb = params.get(i).resolveBinding();
            if (pb != null) paramMap.put(pb.getKey(), args.get(i));
        }

        List<SimpleName> origNames = simpleNames(originalExpr);
        List<SimpleName> copNames = simpleNames(copiedExpr);

        for (int i = 0; i < Math.min(origNames.size(), copNames.size()); i++) {
            SimpleName origName = origNames.get(i);
            SimpleName copName = copNames.get(i);

            if (!(origName.resolveBinding() instanceof IVariableBinding variable)) continue;

            Expression argCopy = paramMap.get(variable.getKey());
            if (argCopy != null) {
                replaceNode(copName, ASTNode.copySubtree(ast, argCopy));
                continue;
            }

            if (receiverExpr != null && variable.isField()
                    && !isStatic(variable.getModifiers())
                    && isUnqualifiedFieldName(origName)) {
                FieldAccess access = ast.newFieldAccess();
                access.setExpression((Expression) ASTNode.copySubtree(ast, receiverExpr));
                access.setName(ast.newSimpleName(variable.getName()));
                replaceNode(copName, access);
            }
        }

        if (receiverExpr != null) {
            List<ThisExpression> origThis = thisExpressions(originalExpr);
            List<ThisExpression> copThis = thisExpressions(copiedExpr);
            for (int i = 0; i < Math.min(origThis.size(), copThis.size()); i++) {
                if (origThis.get(i).getQualifier() == null) {
                    replaceNode(copThis.get(i), ASTNode.copySubtree(ast, receiverExpr));
                }
            }
        }
    }

    private static boolean isUnqualifiedFieldName(SimpleName name) {
        ASTNode parent = name.getParent();
        if (parent instanceof FieldAccess access) {
            return access.getName() != name;
        }
        return !(parent instanceof org.eclipse.jdt.core.dom.QualifiedName)
                && !(parent instanceof org.eclipse.jdt.core.dom.SuperFieldAccess);
    }

    // -------------------------------------------------------------------------
    // AST node replacement (identical to InlineMethodTransformation.replaceNode)
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static void replaceNode(ASTNode oldNode, ASTNode replacement) {
        ASTNode parent = oldNode.getParent();
        if (parent == null) {
            throw new IllegalStateException("Cannot replace a detached AST node");
        }
        for (StructuralPropertyDescriptor property
                : (List<StructuralPropertyDescriptor>) parent.structuralPropertiesForType()) {
            Object value = parent.getStructuralProperty(property);
            if (value == oldNode) {
                parent.setStructuralProperty(property, replacement);
                return;
            }
            if (property instanceof ChildListPropertyDescriptor && value instanceof List<?>) {
                List<ASTNode> children = (List<ASTNode>) value;
                int index = children.indexOf(oldNode);
                if (index >= 0) {
                    children.set(index, replacement);
                    return;
                }
            }
        }
        throw new IllegalStateException(
                "Cannot find child property for " + oldNode.getClass().getSimpleName());
    }

    private static String applyRewrite(CompilationUnit cu, String source, ASTRewrite rewrite) {
        try {
            Document document = new Document(source);
            TextEdit edits = rewrite.rewriteAST(document, null);
            edits.apply(document);
            return document.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply getter inline edits", e);
        }
    }
}
