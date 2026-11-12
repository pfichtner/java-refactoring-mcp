package com.github.pfichtner.refactoring.inlining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * Converts anonymous class declarations that implement a functional interface
 * into lambda expressions.
 *
 * <p>Safety conditions: the anonymous class must implement exactly one
 * functional interface, have exactly one body declaration (the SAM override),
 * and that method body must not use unqualified {@code this} or {@code super}.
 * Bodies with a single {@code return expr;} or a single statement expression
 * produce an expression-form lambda; all other bodies produce a block lambda.
 */
final class AnonymousClassFlatteningTransformation {

    private AnonymousClassFlatteningTransformation() {}

    @SuppressWarnings("unchecked")
    static List<ClassInstanceCreation> collectCandidates(CompilationUnit cu) {
        List<ClassInstanceCreation> results = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation node) {
                if (node.getAnonymousClassDeclaration() != null) {
                    results.add(node);
                }
                return true;
            }
        });
        Collections.reverse(results);
        return results;
    }

    static String typeName(ClassInstanceCreation node) {
        ITypeBinding anon = node.resolveTypeBinding();
        if (anon != null) {
            ITypeBinding[] ifaces = anon.getInterfaces();
            if (ifaces.length == 1) return ifaces[0].getName();
        }
        return "anonymous";
    }

    @SuppressWarnings("unchecked")
    static String tryApply(CompilationUnit cu, String source, ClassInstanceCreation node) {
        if (rejectionReason(node) != null) return null;

        AnonymousClassDeclaration acd = node.getAnonymousClassDeclaration();
        List<BodyDeclaration> decls = acd.bodyDeclarations();
        MethodDeclaration samMethod = (MethodDeclaration) decls.get(0);
        Block samBody = samMethod.getBody();
        List<SingleVariableDeclaration> params = samMethod.parameters();

        AST ast = cu.getAST();
        LambdaExpression lambda = ast.newLambdaExpression();

        for (SingleVariableDeclaration param : params) {
            VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
            fragment.setName(ast.newSimpleName(param.getName().getIdentifier()));
            lambda.parameters().add(fragment);
        }

        lambda.setBody(flattenBody(ast, samBody));

        ASTRewrite rewrite = ASTRewrite.create(ast);
        rewrite.replace(node, lambda, null);

        try {
            Document document = new Document(source);
            TextEdit edits = rewrite.rewriteAST(document, null);
            edits.apply(document);
            return document.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply anonymous class flattening", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ASTNode flattenBody(AST ast, Block block) {
        List<Statement> stmts = block.statements();

        if (stmts.size() == 1 && stmts.get(0) instanceof ReturnStatement ret) {
            Expression expr = ret.getExpression();
            if (expr != null) {
                return ASTNode.copySubtree(ast, expr);
            }
        }

        if (stmts.size() == 1 && stmts.get(0) instanceof ExpressionStatement es) {
            Expression expr = es.getExpression();
            if (isStatementExpression(expr)) {
                return ASTNode.copySubtree(ast, expr);
            }
        }

        return ASTNode.copySubtree(ast, block);
    }

    private static boolean isStatementExpression(Expression expr) {
        return expr instanceof MethodInvocation
                || expr instanceof SuperMethodInvocation
                || expr instanceof ClassInstanceCreation
                || expr instanceof Assignment
                || expr instanceof PostfixExpression
                || (expr instanceof PrefixExpression prefix
                        && (prefix.getOperator() == PrefixExpression.Operator.INCREMENT
                                || prefix.getOperator() == PrefixExpression.Operator.DECREMENT));
    }

    @SuppressWarnings("unchecked")
    static String rejectionReason(ClassInstanceCreation node) {
        AnonymousClassDeclaration acd = node.getAnonymousClassDeclaration();
        if (acd == null) return "not an anonymous class creation";

        List<Expression> args = node.arguments();
        if (!args.isEmpty()) {
            return "anonymous class with constructor arguments cannot be converted to lambda";
        }

        ITypeBinding anon = node.resolveTypeBinding();
        if (anon == null) return "cannot resolve anonymous class type binding";

        ITypeBinding[] ifaces = anon.getInterfaces();
        if (ifaces.length != 1) {
            return "anonymous class must implement exactly one interface";
        }
        IMethodBinding sam = ifaces[0].getFunctionalInterfaceMethod();
        if (sam == null) {
            return "implemented interface is not a functional interface";
        }

        List<BodyDeclaration> decls = acd.bodyDeclarations();
        if (decls.size() != 1) {
            return "anonymous class has " + decls.size()
                    + " body declarations; expected exactly the SAM override";
        }
        if (!(decls.get(0) instanceof MethodDeclaration samMethod)) {
            return "anonymous class body declaration is not a method";
        }

        IMethodBinding samBinding = samMethod.resolveBinding();
        if (samBinding == null) return "cannot resolve SAM method binding";
        if (samBinding.getTypeParameters().length > 0) {
            return "SAM method has type parameters; cannot convert to lambda";
        }
        if (samBinding.isVarargs()) {
            return "SAM method is varargs; cannot convert to lambda";
        }
        if (Modifier.isSynchronized(samBinding.getModifiers())) {
            return "SAM method is synchronized; monitor semantics would change in lambda";
        }

        Block body = samMethod.getBody();
        if (body == null) return "SAM method has no body";

        String thisOrSuper = checkThisOrSuperUsage(body);
        if (thisOrSuper != null) return thisOrSuper;

        if (hasNestedTypeDeclaration(body)) {
            return "SAM method body contains a local or anonymous class";
        }

        return null;
    }

    private static String checkThisOrSuperUsage(Block body) {
        String[] reason = {null};
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(ThisExpression node) {
                if (node.getQualifier() == null) {
                    reason[0] = "SAM method body uses 'this'; in a lambda 'this' would refer "
                            + "to the enclosing type, not the anonymous class instance";
                }
                return reason[0] == null;
            }

            @Override
            public boolean visit(SuperMethodInvocation node) {
                reason[0] = "SAM method body uses 'super'; not valid in a lambda";
                return false;
            }

            @Override
            public boolean visit(SuperFieldAccess node) {
                reason[0] = "SAM method body uses 'super'; not valid in a lambda";
                return false;
            }

            @Override
            public boolean visit(LambdaExpression node) {
                return false;
            }

            @Override
            public boolean visit(AnonymousClassDeclaration node) {
                return false;
            }
        });
        return reason[0];
    }

    private static boolean hasNestedTypeDeclaration(Block body) {
        boolean[] found = {false};
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(AnonymousClassDeclaration node) {
                found[0] = true;
                return false;
            }

            @Override
            public boolean visit(TypeDeclarationStatement node) {
                found[0] = true;
                return false;
            }

            @Override
            public boolean visit(LambdaExpression node) {
                return false;
            }
        });
        return found[0];
    }
}
