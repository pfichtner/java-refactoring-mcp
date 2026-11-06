package com.github.pfichtner.refactoring.inlining;

import static org.eclipse.jdt.core.dom.Modifier.isStatic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

/**
 * Adds the imports a copied callee body or signature needs in the caller.
 * Name conflicts are rejected rather than guessed. Imports are inserted as
 * AST nodes; no source text is edited directly.
 */
final class InlineImports {

    private record Request(
            boolean isStatic, boolean field, String qualifiedName,
            String simpleName, String packageName, boolean nested) {}

    private final List<ImportDeclaration> declarations;

    private InlineImports(List<ImportDeclaration> declarations) {
        this.declarations = declarations;
    }

    static InlineImports create(
            CompilationUnit callerCu, MethodDeclaration decl, ITypeBinding callerType) {

        Map<String, Request> requests = new HashMap<>();
        ASTVisitor collector = new ASTVisitor() {
            @Override
            public boolean visit(SimpleName name) {
                collect(name, requests, callerType);
                return true;
            }
        };

        decl.getBody().accept(collector);
        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> params = decl.parameters();
        for (SingleVariableDeclaration param : params) {
            param.getType().accept(collector);
        }
        if (decl.getReturnType2() != null) {
            decl.getReturnType2().accept(collector);
        }
        return new InlineImports(build(callerCu, callerType, requests.values().stream()
                .sorted(Comparator.comparing(Request::isStatic)
                        .thenComparing(Request::qualifiedName))
                .toList()));
    }

    void insertInto(ASTRewrite rewrite, CompilationUnit cu) {
        if (declarations.isEmpty()) return;
        ListRewrite list = rewrite.getListRewrite(cu, CompilationUnit.IMPORTS_PROPERTY);
        for (ImportDeclaration declaration : declarations) {
            list.insertLast(declaration, null);
        }
    }

    private static void collect(
            SimpleName name, Map<String, Request> requests, ITypeBinding callerType) {

        IBinding binding = name.resolveBinding();
        if (binding instanceof ITypeBinding type) {
            if (isRightmostOfQualifiedName(name)) return;
            ITypeBinding declaration = usableType(type);
            if (declaration == null) return;
            String simple = name.getIdentifier();
            Request request = new Request(false, false, declaration.getQualifiedName(),
                    simple, packageName(declaration), declaration.getDeclaringClass() != null);
            merge(requests, request);
            return;
        }

        if (!isUnqualifiedReference(name)) return;

        if (binding instanceof IMethodBinding method) {
            if (!isStatic(method.getModifiers())) return;
            ITypeBinding declaring = method.getDeclaringClass();
            if (isInCallerHierarchy(declaring, callerType)) return;
            Request request = new Request(true, false,
                    declaring.getQualifiedName() + "." + name.getIdentifier(),
                    name.getIdentifier(), packageName(declaring), false);
            merge(requests, request);
        } else if (binding instanceof IVariableBinding variable && variable.isField()) {
            if (!isStatic(variable.getModifiers())) return;
            ITypeBinding declaring = variable.getDeclaringClass();
            if (isInCallerHierarchy(declaring, callerType)) return;
            Request request = new Request(true, true,
                    declaring.getQualifiedName() + "." + name.getIdentifier(),
                    name.getIdentifier(), packageName(declaring), false);
            merge(requests, request);
        }
    }

    private static void merge(Map<String, Request> requests, Request request) {
        String key = (request.isStatic() ? "static:" : "type:") + request.simpleName();
        Request existing = requests.putIfAbsent(key, request);
        if (existing != null && !existing.qualifiedName().equals(request.qualifiedName())) {
            throw conflict(request.simpleName(), existing.qualifiedName(), request.qualifiedName());
        }
    }

    private static List<ImportDeclaration> build(
            CompilationUnit callerCu, ITypeBinding callerType, List<Request> requests) {

        Map<String, String> existingNormal = existingImports(callerCu, false);
        Map<String, String> existingStatic = existingImports(callerCu, true);
        Map<String, String> declaredTypes = declaredTypes(callerCu);
        String callerPackage = packageName(callerType);
        List<ImportDeclaration> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (Request request : requests) {
            if (request.isStatic()) {
                if (callerMemberExists(callerType, request)) {
                    throw conflict(request.simpleName(), "a caller member", request.qualifiedName());
                }
                String existing = existingStatic.get(request.simpleName());
                if (existing != null) {
                    if (!existing.equals(request.qualifiedName())) {
                        throw conflict(request.simpleName(), existing, request.qualifiedName());
                    }
                    continue;
                }
            } else {
                String declared = declaredTypes.get(request.simpleName());
                if (declared != null) {
                    if (!declared.equals(request.qualifiedName())) {
                        throw conflict(request.simpleName(), declared, request.qualifiedName());
                    }
                    continue;
                }
                String existing = existingNormal.get(request.simpleName());
                if (existing != null) {
                    if (!existing.equals(request.qualifiedName())) {
                        throw conflict(request.simpleName(), existing, request.qualifiedName());
                    }
                    continue;
                }
                if (!request.nested()
                        && (request.packageName().equals(callerPackage)
                                || "java.lang".equals(request.packageName()))) {
                    continue;
                }
            }

            String key = (request.isStatic() ? "static:" : "type:") + request.qualifiedName();
            if (!seen.add(key)) continue;
            result.add(importDeclaration(callerCu, request));
        }
        return result;
    }

    private static ImportDeclaration importDeclaration(CompilationUnit cu, Request request) {
        ImportDeclaration declaration = cu.getAST().newImportDeclaration();
        declaration.setStatic(request.isStatic());
        declaration.setName(cu.getAST().newName(request.qualifiedName()));
        return declaration;
    }

    private static Map<String, String> existingImports(CompilationUnit cu, boolean statics) {
        Map<String, String> result = new HashMap<>();
        for (Object item : cu.imports()) {
            ImportDeclaration declaration = (ImportDeclaration) item;
            if (declaration.isStatic() != statics || declaration.isOnDemand()) continue;
            String qualified = declaration.getName().getFullyQualifiedName();
            result.put(simpleName(qualified), qualified);
        }
        return result;
    }

    private static Map<String, String> declaredTypes(CompilationUnit cu) {
        Map<String, String> result = new HashMap<>();
        cu.accept(new ASTVisitor() {
            @Override
            public void preVisit(ASTNode node) {
                if (node instanceof AbstractTypeDeclaration type) {
                    ITypeBinding binding = type.resolveBinding();
                    if (binding != null) {
                        result.putIfAbsent(binding.getName(), binding.getQualifiedName());
                    }
                }
            }
        });
        return result;
    }

    private static boolean callerMemberExists(ITypeBinding callerType, Request request) {
        if (callerType == null) return false;
        for (ITypeBinding enclosing = callerType; enclosing != null; enclosing = enclosing.getDeclaringClass()) {
            for (ITypeBinding type = enclosing; type != null; type = type.getSuperclass()) {
                if (request.field()) {
                    for (IVariableBinding field : type.getDeclaredFields()) {
                        if (field.getName().equals(request.simpleName())) return true;
                    }
                } else {
                    for (IMethodBinding method : type.getDeclaredMethods()) {
                        if (method.getName().equals(request.simpleName())) return true;
                    }
                }
            }
        }
        return false;
    }

    private static ITypeBinding usableType(ITypeBinding type) {
        if (type.isPrimitive() || type.isArray() || type.isTypeVariable()
                || type.isWildcardType() || type.isCapture() || type.isNullType()
                || type.isLocal() || type.isAnonymous()) {
            return null;
        }
        return type.getTypeDeclaration();
    }

    private static boolean isRightmostOfQualifiedName(SimpleName name) {
        ASTNode parent = name.getParent();
        return parent instanceof QualifiedName qualified && qualified.getName() == name;
    }

    private static boolean isUnqualifiedReference(SimpleName name) {
        ASTNode parent = name.getParent();
        if (parent instanceof QualifiedName) return false;
        if (parent instanceof FieldAccess) return false;
        if (parent instanceof SuperFieldAccess) return false;
        if (parent instanceof MethodInvocation invocation) {
            if (invocation.getExpression() == name) return false;
            if (invocation.getName() == name) return invocation.getExpression() == null;
            return true;
        }
        if (parent instanceof SuperMethodInvocation invocation) {
            return invocation.getName() != name;
        }
        if (parent instanceof MethodReference) return false;
        return true;
    }

    private static boolean isInCallerHierarchy(ITypeBinding declaring, ITypeBinding callerType) {
        if (declaring == null || callerType == null) return false;
        ITypeBinding declaration = declaring.getTypeDeclaration();
        for (ITypeBinding type = callerType; type != null; type = type.getDeclaringClass()) {
            if (declaration.getKey().equals(type.getTypeDeclaration().getKey())) return true;
        }
        return callerType.isSubTypeCompatible(declaration);
    }

    private static String packageName(ITypeBinding type) {
        if (type == null) return "";
        IPackageBinding pkg = type.getPackage();
        return pkg == null ? "" : pkg.getName();
    }

    private static String simpleName(String qualifiedName) {
        int dot = qualifiedName.lastIndexOf('.');
        return dot < 0 ? qualifiedName : qualifiedName.substring(dot + 1);
    }

    private static InlineRejectedException conflict(
            String simpleName, String existing, String requested) {
        return new InlineRejectedException("import conflict: '" + simpleName
                + "' is already " + existing + " but the callee needs " + requested);
    }
}
