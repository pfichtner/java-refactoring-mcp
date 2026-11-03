package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.enclosingMethod;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.enclosingNamedType;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.isVoid;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.ownReturns;
import static org.eclipse.jdt.core.dom.Modifier.isFinal;
import static org.eclipse.jdt.core.dom.Modifier.isPrivate;
import static org.eclipse.jdt.core.dom.Modifier.isStatic;
import static org.eclipse.jdt.core.dom.Modifier.isSynchronized;

import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.ThisExpression;

/**
 * Rejects method inlining when Java execution semantics cannot be preserved
 * without machinery the spike does not implement.
 */
final class SemanticSafetyAnalyzer {

    private SemanticSafetyAnalyzer() {}

    /** Returns the reason an inline is unsafe, or {@code null} when it is accepted. */
    static String rejectionReason(
            CompilationUnit cu, MethodInvocation call, MethodDeclaration decl, IMethodBinding binding) {

        Block body = decl.getBody();
        if (body == null) {
            return "method has no body (abstract or native)";
        }
        if (binding.isConstructor()) {
            return "constructor calls are not supported by method inlining";
        }

        int modifiers = binding.getModifiers();
        if (isSynchronized(modifiers)) {
            return "synchronized method: inlining would remove the method-level monitor";
        }
        if (binding.getTypeParameters().length > 0) {
            return "generic method type parameters are not supported by the spike";
        }
        if (binding.isVarargs()) {
            return "varargs methods are not supported by the spike";
        }
        String checked = firstCheckedException(cu, binding);
        if (checked != null) {
            return "method declares checked exception " + checked
                    + "; inlining could change checked-exception handling at the call site";
        }

        MethodDeclaration caller = enclosingMethod(call);
        if (caller == null) {
            return "call is not inside a method body";
        }
        IMethodBinding callerBinding = caller.resolveBinding();
        if (callerBinding != null
                && binding.getMethodDeclaration().getKey()
                        .equals(callerBinding.getMethodDeclaration().getKey())) {
            return "recursive call";
        }

        ITypeBinding declaringType = binding.getDeclaringClass();
        ITypeBinding callType = enclosingNamedType(call);
        if (declaringType == null || callType == null) {
            return "cannot determine the enclosing named types";
        }
        if (!declaringType.getTypeDeclaration().getKey()
                .equals(callType.getTypeDeclaration().getKey())) {
            return "cross-type inlining is not supported by the spike";
        }

        Expression receiver = call.getExpression();
        boolean staticMethod = isStatic(modifiers);
        if (!staticMethod) {
            if (receiver != null && !(receiver instanceof ThisExpression)) {
                return "instance method is invoked on an explicit receiver; 'this' would change";
            }
            if (!isPrivate(modifiers) && !isFinal(modifiers)
                    && !isFinal(declaringType.getModifiers())) {
                return "non-final instance method on a non-final type: virtual dispatch would change";
            }
        } else if (receiver != null && !isTypeExpression(receiver)) {
            return "static method is invoked through a value expression; its evaluation would be lost";
        }

        boolean valueReturning = !isVoid(binding);
        var returns = ownReturns(body);
        if (valueReturning && returns.isEmpty()) {
            return "value-returning method has no return statement in its own body";
        }
        if (!valueReturning && returns.isEmpty() && !InliningSupport.canCompleteNormally(body)) {
            return "method body cannot complete normally; inlining could make following code unreachable";
        }
        for (ReturnStatement rs : returns) {
            if (valueReturning && rs.getExpression() == null) {
                return "value-returning method contains a bare return statement";
            }
            if (!valueReturning && rs.getExpression() != null) {
                return "void method contains a return with a value";
            }
        }
        return null;
    }

    private static boolean isTypeExpression(Expression expression) {
        if (expression instanceof Name name) {
            IBinding binding = name.resolveBinding();
            return binding instanceof ITypeBinding;
        }
        return false;
    }

    private static String firstCheckedException(CompilationUnit cu, IMethodBinding binding) {
        ITypeBinding[] exceptions = binding.getExceptionTypes();
        if (exceptions.length == 0) return null;

        var ast = cu.getAST();
        ITypeBinding throwable = ast.resolveWellKnownType("java.lang.Throwable");
        ITypeBinding runtime = ast.resolveWellKnownType("java.lang.RuntimeException");
        ITypeBinding error = ast.resolveWellKnownType("java.lang.Error");

        for (ITypeBinding exception : exceptions) {
            ITypeBinding erasure = exception.getErasure();
            if (throwable == null || runtime == null || error == null) {
                String name = erasure.getQualifiedName();
                if (!"java.lang.RuntimeException".equals(name) && !"java.lang.Error".equals(name)) {
                    return name;
                }
                continue;
            }
            if (erasure.isSubTypeCompatible(throwable)
                    && !erasure.isSubTypeCompatible(runtime)
                    && !erasure.isSubTypeCompatible(error)) {
                return erasure.getQualifiedName();
            }
        }
        return null;
    }
}
