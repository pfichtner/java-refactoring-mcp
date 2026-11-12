package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.isDelegatingBody;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.simpleNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * Inlines a delegation method call by direct call substitution.
 *
 * <p>A delegation method has a body of exactly one statement that is a
 * {@link MethodInvocation}: either {@code return inner(args);} or the void
 * form {@code inner(args);}. Such a method can be inlined at <em>any</em>
 * call-site position — including nested expressions — by replacing the outer
 * call with the inner call, with outer-call arguments substituted for the
 * outer method's parameters.
 *
 * <p>Falls back to {@code null} when the method is not a delegation method,
 * when the inner call cannot be accessed from the call site, or when argument
 * substitution would double-evaluate a non-pure outer argument.
 */
final class DelegationEliminationTransformation {

    private DelegationEliminationTransformation() {}

    /**
     * Attempts a delegation substitution. Returns the rewritten source, or
     * {@code null} when the method does not qualify for delegation inlining.
     */
    static String tryApply(
            CompilationUnit cu, String source,
            MethodInvocation outerCall, MethodDeclaration outerDecl,
            IMethodBinding outerBinding) {

        if (!isDelegatingBody(outerDecl)) return null;

        MethodInvocation innerCall = extractInnerCall(outerDecl);
        IMethodBinding innerBinding = innerCall.resolveMethodBinding();
        if (innerBinding == null) return null;
        if (innerBinding.isVarargs()) return null;

        // Inner receiver must be absent or unqualified this.
        Expression innerReceiver = innerCall.getExpression();
        if (innerReceiver != null) {
            if (!(innerReceiver instanceof ThisExpression te && te.getQualifier() == null)) {
                return null;
            }
        }

        // Inner method must be declared in the same type as the outer method so that
        // the inner method is accessible from wherever the outer method is called.
        ITypeBinding outerType = outerBinding.getDeclaringClass();
        ITypeBinding innerType = innerBinding.getDeclaringClass();
        if (outerType == null || innerType == null) return null;
        if (!outerType.getKey().equals(innerType.getKey())) return null;

        // Outer call must have no explicit receiver or unqualified this.
        Expression outerReceiver = outerCall.getExpression();
        if (outerReceiver != null
                && !(outerReceiver instanceof ThisExpression te && te.getQualifier() == null)) {
            return null;
        }

        // Build param → outer-arg map.
        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> params = outerDecl.parameters();
        @SuppressWarnings("unchecked")
        List<Expression> outerArgs = outerCall.arguments();
        if (params.size() != outerArgs.size()) return null;

        Map<String, Expression> paramMap = new HashMap<>();
        for (int i = 0; i < params.size(); i++) {
            IVariableBinding pb = params.get(i).resolveBinding();
            if (pb == null) return null;
            paramMap.put(pb.getKey(), outerArgs.get(i));
        }

        // Validate that substitution is safe.
        @SuppressWarnings("unchecked")
        List<Expression> innerArgs = innerCall.arguments();
        if (!canSubstitute(innerArgs, paramMap, outerArgs, params)) return null;

        // Build the replacement call by copying the inner call and substituting params.
        AST ast = cu.getAST();
        MethodInvocation replacement = (MethodInvocation) ASTNode.copySubtree(ast, innerCall);
        substituteParams(ast, innerCall, replacement, paramMap);

        ASTRewrite rewrite = ASTRewrite.create(ast);
        rewrite.replace(outerCall, replacement, null);
        return applyRewrite(cu, source, rewrite);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static MethodInvocation extractInnerCall(MethodDeclaration decl) {
        @SuppressWarnings("unchecked")
        List<Statement> stmts = decl.getBody().statements();
        Statement stmt = stmts.get(0);
        if (stmt instanceof ReturnStatement ret) {
            return (MethodInvocation) ret.getExpression();
        }
        return (MethodInvocation) ((ExpressionStatement) stmt).getExpression();
    }

    /**
     * Returns true when each inner arg is a simple param ref, a pure literal, or
     * an accessible field reference, and no non-pure outer arg is used more than
     * once in the inner args.
     */
    private static boolean canSubstitute(
            List<Expression> innerArgs,
            Map<String, Expression> paramMap,
            List<Expression> outerArgs,
            List<SingleVariableDeclaration> params) {

        Map<String, Integer> useCounts = new HashMap<>();
        for (Expression innerArg : innerArgs) {
            if (!isSafeArg(innerArg, paramMap.keySet())) return false;
            String key = paramKey(innerArg);
            if (key != null) {
                useCounts.merge(key, 1, Integer::sum);
            }
        }

        // If a param is used more than once, its outer arg must be side-effect-free.
        for (int i = 0; i < params.size(); i++) {
            IVariableBinding pb = params.get(i).resolveBinding();
            if (pb == null) return false;
            int count = useCounts.getOrDefault(pb.getKey(), 0);
            if (count > 1 && !isPureOuterArg(outerArgs.get(i))) return false;
        }
        return true;
    }

    /**
     * True when {@code innerArg} is a simple param reference, a pure literal,
     * or an unqualified field access — expressions that require no temporary and
     * remain in scope at the outer call site.
     */
    private static boolean isSafeArg(Expression innerArg, Set<String> paramKeys) {
        String key = paramKey(innerArg);
        if (key != null) return paramKeys.contains(key);
        if (isPureLiteral(innerArg)) return true;
        // Unqualified field name: accessible from same class.
        if (innerArg instanceof SimpleName name
                && name.resolveBinding() instanceof IVariableBinding var
                && var.isField()) {
            return true;
        }
        // this.field: accessible from same class.
        if (innerArg instanceof FieldAccess fa
                && fa.getExpression() instanceof ThisExpression te
                && te.getQualifier() == null) {
            return true;
        }
        return false;
    }

    private static String paramKey(Expression expr) {
        if (expr instanceof SimpleName name
                && name.resolveBinding() instanceof IVariableBinding var
                && var.isParameter()) {
            return var.getKey();
        }
        return null;
    }

    private static boolean isPureLiteral(Expression expr) {
        return expr instanceof NumberLiteral
                || expr instanceof BooleanLiteral
                || expr instanceof CharacterLiteral
                || expr instanceof StringLiteral
                || expr instanceof NullLiteral;
    }

    /** True when the outer arg is side-effect-free and can be duplicated safely. */
    private static boolean isPureOuterArg(Expression arg) {
        if (isPureLiteral(arg)) return true;
        if (arg instanceof SimpleName name
                && name.resolveBinding() instanceof IVariableBinding var
                && !var.isField() && !var.isEnumConstant()) {
            return true; // local variable or parameter: no side effects on read
        }
        return false;
    }

    /**
     * Substitutes outer parameters with their corresponding outer arguments in
     * a parallel walk of the original and copied inner calls.
     */
    private static void substituteParams(
            AST ast,
            MethodInvocation originalInnerCall,
            MethodInvocation copiedInnerCall,
            Map<String, Expression> paramMap) {

        List<SimpleName> origNames = simpleNames(originalInnerCall);
        List<SimpleName> copNames = simpleNames(copiedInnerCall);

        for (int i = 0; i < Math.min(origNames.size(), copNames.size()); i++) {
            SimpleName origName = origNames.get(i);
            if (!(origName.resolveBinding() instanceof IVariableBinding var)) continue;
            Expression outerArg = paramMap.get(var.getKey());
            if (outerArg != null) {
                replaceNode(copNames.get(i), ASTNode.copySubtree(ast, outerArg));
            }
        }
    }

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
            throw new IllegalStateException("Failed to apply delegation elimination edits", e);
        }
    }
}
