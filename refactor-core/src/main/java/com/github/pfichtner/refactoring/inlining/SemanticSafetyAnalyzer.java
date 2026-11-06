package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.enclosingMethod;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.enclosingNamedType;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.isVoid;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.ownReturns;
import static org.eclipse.jdt.core.dom.Modifier.isFinal;
import static org.eclipse.jdt.core.dom.Modifier.isPrivate;
import static org.eclipse.jdt.core.dom.Modifier.isProtected;
import static org.eclipse.jdt.core.dom.Modifier.isPublic;
import static org.eclipse.jdt.core.dom.Modifier.isStatic;
import static org.eclipse.jdt.core.dom.Modifier.isSynchronized;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
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
        boolean sameType = declaringType.getTypeDeclaration().getKey()
                .equals(callType.getTypeDeclaration().getKey());
        if (!sameType) {
            String crossType = crossTypeRejection(decl, binding, callType, declaringType);
            if (crossType != null) return crossType;
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

    private static String crossTypeRejection(
            MethodDeclaration decl, IMethodBinding binding,
            ITypeBinding callType, ITypeBinding declaringType) {

        if (isStatic(binding.getModifiers())) {
            return "cross-type static inlining would remove the class-initialization trigger; "
                    + "static cross-type inlining is not supported by the spike";
        }
        if (!callType.isSubTypeCompatible(declaringType)) {
            return "cross-type callee is not a supertype of the caller; 'this' would change";
        }
        if (declaringType.getTypeDeclaration().getTypeParameters().length > 0) {
            return "generic declaring types are not supported by cross-type inlining";
        }
        String superUsage = superOrQualifiedThis(decl.getBody());
        if (superUsage != null) return superUsage;
        String signature = inaccessibleSignature(decl, callType);
        if (signature != null) return signature;
        return inaccessibleBody(decl.getBody(), callType);
    }

    private static String superOrQualifiedThis(Block body) {
        String[] reason = {null};
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(SuperMethodInvocation node) {
                reason[0] = "cross-type callee body uses 'super'; it would bind to the caller's superclass";
                return false;
            }

            @Override
            public boolean visit(SuperFieldAccess node) {
                reason[0] = "cross-type callee body uses 'super'; it would bind to the caller's superclass";
                return false;
            }

            @Override
            public boolean visit(ThisExpression node) {
                if (node.getQualifier() != null) {
                    reason[0] = "cross-type callee body uses qualified 'this'; the enclosing instance would change";
                }
                return reason[0] == null;
            }
        });
        return reason[0];
    }

    @SuppressWarnings("unchecked")
    private static String inaccessibleSignature(MethodDeclaration decl, ITypeBinding callerType) {
        List<SingleVariableDeclaration> params = decl.parameters();
        for (SingleVariableDeclaration param : params) {
            String reason = inaccessibleType(param.getType().resolveBinding(), callerType);
            if (reason != null) return reason;
        }
        if (decl.getReturnType2() != null) {
            return inaccessibleType(decl.getReturnType2().resolveBinding(), callerType);
        }
        return null;
    }

    private static String inaccessibleBody(Block body, ITypeBinding callerType) {
        String[] reason = {null};
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName name) {
                if (reason[0] != null) return false;
                IBinding binding = name.resolveBinding();
                if (binding instanceof ITypeBinding type) {
                    reason[0] = inaccessibleType(type, callerType);
                } else if (binding instanceof IMethodBinding method) {
                    reason[0] = inaccessibleMember(
                            "method", method.getDeclaringClass(), method.getModifiers(), name, callerType);
                } else if (binding instanceof IVariableBinding variable
                        && (variable.isField() || variable.isEnumConstant())) {
                    reason[0] = inaccessibleMember(
                            "field", variable.getDeclaringClass(), variable.getModifiers(), name, callerType);
                }
                return reason[0] == null;
            }
        });
        return reason[0];
    }

    private static String inaccessibleType(ITypeBinding type, ITypeBinding callerType) {
        if (type == null) return null;
        if (type.isArray()) return inaccessibleType(type.getElementType(), callerType);
        if (type.isPrimitive() || type.isTypeVariable() || type.isWildcardType()
                || type.isCapture() || type.isNullType() || type.isLocal() || type.isAnonymous()) {
            return null;
        }
        ITypeBinding declaration = type.getTypeDeclaration();
        if (declaration == null) return null;
        if (isPrivate(declaration.getModifiers())) {
            return "cross-type callee references private type " + declaration.getQualifiedName();
        }
        if (!samePackage(declaration, callerType) && !isPublic(declaration.getModifiers())) {
            return "cross-type callee references inaccessible type "
                    + declaration.getQualifiedName();
        }
        ITypeBinding enclosing = declaration.getDeclaringClass();
        return enclosing == null ? null : inaccessibleType(enclosing, callerType);
    }

    private static String inaccessibleMember(
            String kind, ITypeBinding declaring, int modifiers,
            SimpleName name, ITypeBinding callerType) {

        if (declaring == null) return null;
        ITypeBinding declaration = declaring.getTypeDeclaration();
        String typeReason = inaccessibleType(declaration, callerType);
        if (typeReason != null) return typeReason;

        String memberName = declaration.getQualifiedName() + "." + name.getIdentifier();
        if (isPrivate(modifiers)) {
            return "cross-type callee references private " + kind + " " + memberName;
        }
        if (samePackage(declaration, callerType)) return null;
        if (isPublic(modifiers)) return null;
        if (isProtected(modifiers) && callerType.isSubTypeCompatible(declaration)
                && protectedAccessThroughThisOrUnqualified(name)) {
            return null;
        }
        return "cross-type callee references inaccessible " + kind + " " + memberName;
    }

    private static boolean protectedAccessThroughThisOrUnqualified(SimpleName name) {
        ASTNode parent = name.getParent();
        if (parent instanceof MethodInvocation invocation) {
            return invocation.getName() == name
                    && (invocation.getExpression() == null
                            || invocation.getExpression() instanceof ThisExpression);
        }
        if (parent instanceof FieldAccess access) {
            return access.getName() == name
                    && access.getExpression() instanceof ThisExpression;
        }
        if (parent instanceof SuperFieldAccess
                || parent instanceof SuperMethodInvocation
                || parent instanceof QualifiedName) {
            return false;
        }
        return true;
    }

    private static boolean samePackage(ITypeBinding a, ITypeBinding b) {
        IPackageBinding packageA = a.getPackage();
        IPackageBinding packageB = b.getPackage();
        if (packageA == null || packageB == null) return packageA == packageB;
        return packageA.getName().equals(packageB.getName());
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
