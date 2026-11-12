package com.github.pfichtner.refactoring.inlining;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EmptyStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.YieldStatement;

/** Small AST navigation helpers shared by the inlining spike. */
final class InliningSupport {

    private InliningSupport() {}

    static MethodDeclaration enclosingMethod(ASTNode node) {
        for (ASTNode n = node; n != null; n = n.getParent()) {
            if (n instanceof MethodDeclaration md) return md;
        }
        return null;
    }

    /** Returns the nearest named type, or {@code null} when the node sits inside an anonymous class. */
    static ITypeBinding enclosingNamedType(ASTNode node) {
        for (ASTNode n = node; n != null; n = n.getParent()) {
            if (n instanceof AnonymousClassDeclaration) return null;
            if (n instanceof AbstractTypeDeclaration type) return type.resolveBinding();
        }
        return null;
    }

    static boolean isVoid(IMethodBinding binding) {
        ITypeBinding returnType = binding.getReturnType();
        return returnType != null && returnType.isPrimitive()
                && "void".equals(returnType.getName());
    }

    static MethodDeclaration findMethodDeclaration(CompilationUnit cu, IMethodBinding binding) {
        String key = binding.getMethodDeclaration().getKey();
        MethodDeclaration[] found = {null};
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                IMethodBinding b = node.resolveBinding();
                if (b != null && key.equals(b.getMethodDeclaration().getKey())) {
                    found[0] = node;
                    return false;
                }
                return found[0] == null;
            }
        });
        return found[0];
    }

    /**
     * Finds the innermost {@link MethodInvocation} or {@link ConstructorInvocation}
     * at {@code offset}, or throws {@link InlineRejectedException} when the node at
     * that position is a call type that is not supported.
     */
    static ASTNode findCallNode(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        while (node != null) {
            if (node instanceof MethodInvocation) return node;
            if (node instanceof ConstructorInvocation) return node;
            if (node instanceof SuperMethodInvocation) {
                throw new InlineRejectedException(
                        "super method calls are not supported by aggressive inlining");
            }
            if (node instanceof ClassInstanceCreation
                    || node instanceof SuperConstructorInvocation) {
                throw new InlineRejectedException(
                        "constructor calls are not supported by aggressive inlining");
            }
            if (node instanceof MethodReference) {
                throw new InlineRejectedException(
                        "method references are not call sites for aggressive inlining");
            }
            node = node.getParent();
        }
        throw new InlineRejectedException("No method call found at offset " + offset + ".");
    }

    /** Convenience wrapper that asserts the found call node is a {@link MethodInvocation}. */
    static MethodInvocation findInvocation(CompilationUnit cu, int offset) {
        ASTNode node = findCallNode(cu, offset);
        if (node instanceof MethodInvocation mi) return mi;
        throw new InlineRejectedException(
                "No method invocation found at offset " + offset
                        + "; found " + node.getClass().getSimpleName() + " instead.");
    }

    /**
     * Returns return statements that belong to this body, skipping returns inside
     * lambdas and nested/anonymous methods.
     */
    static List<ReturnStatement> ownReturns(ASTNode body) {
        List<ReturnStatement> result = new ArrayList<>();
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(ReturnStatement node) {
                result.add(node);
                return false;
            }

            @Override
            public boolean visit(LambdaExpression node) {
                return false;
            }

            @Override
            public boolean visit(MethodDeclaration node) {
                return false;
            }
        });
        return result;
    }

    static List<ThisExpression> thisExpressions(ASTNode node) {
        List<ThisExpression> result = new ArrayList<>();
        node.accept(new ASTVisitor() {
            @Override
            public boolean visit(ThisExpression n) {
                result.add(n);
                return true;
            }
        });
        return result;
    }

    static List<SimpleName> simpleNames(ASTNode node) {
        List<SimpleName> names = new ArrayList<>();
        node.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName name) {
                names.add(name);
                return true;
            }
        });
        return names;
    }

    /**
     * True when the argument can replace one read of the parameter without capture.
     * Purity is intentionally narrow: only literals, unqualified {@code this}, and
     * reads of caller locals/parameters qualify.
     */
    static boolean canSubstituteArgument(
            Expression argument, IVariableBinding parameter, ASTNode body) {

        if (!isPureArgument(argument)) return false;

        int[] uses = {0};
        boolean[] unsafe = {false};
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName name) {
                if (!(name.resolveBinding() instanceof IVariableBinding variable)
                        || !variable.getKey().equals(parameter.getKey())) {
                    return true;
                }
                uses[0]++;
                if (isWriteTarget(name) || hasUnsafeEvaluationContext(name, body)) {
                    unsafe[0] = true;
                }
                return true;
            }
        });
        return uses[0] == 1 && !unsafe[0];
    }

    private static boolean isPureArgument(Expression expression) {
        if (expression instanceof NumberLiteral
                || expression instanceof BooleanLiteral
                || expression instanceof CharacterLiteral
                || expression instanceof StringLiteral
                || expression instanceof NullLiteral) {
            return true;
        }
        if (expression instanceof ThisExpression thisExpression) {
            return thisExpression.getQualifier() == null;
        }
        if (expression instanceof ParenthesizedExpression parenthesized) {
            return isPureArgument(parenthesized.getExpression());
        }
        if (expression instanceof SimpleName name) {
            return name.resolveBinding() instanceof IVariableBinding variable
                    && !variable.isField()
                    && !variable.isEnumConstant();
        }
        return false;
    }

    /**
     * True when the expression evaluates without side effects: literals, unqualified
     * {@code this}, local/parameter reads, and non-short-circuit composites whose
     * sub-expressions are all themselves pure.
     */
    static boolean isPure(Expression expression) {
        if (isPureArgument(expression)) return true;
        if (expression instanceof InfixExpression infix) {
            InfixExpression.Operator op = infix.getOperator();
            if (op == InfixExpression.Operator.CONDITIONAL_AND
                    || op == InfixExpression.Operator.CONDITIONAL_OR) {
                return false;
            }
            if (!isPure(infix.getLeftOperand()) || !isPure(infix.getRightOperand())) return false;
            @SuppressWarnings("unchecked")
            List<Expression> extended = infix.extendedOperands();
            for (Expression ext : extended) {
                if (!isPure(ext)) return false;
            }
            return true;
        }
        if (expression instanceof CastExpression cast) {
            return isPure(cast.getExpression());
        }
        if (expression instanceof PrefixExpression prefix) {
            PrefixExpression.Operator op = prefix.getOperator();
            if (op == PrefixExpression.Operator.INCREMENT
                    || op == PrefixExpression.Operator.DECREMENT) {
                return false;
            }
            return isPure(prefix.getOperand());
        }
        return false;
    }

    /**
     * True when the method body is a single statement that is a {@link MethodInvocation}:
     * either {@code return inner(args);} or the void form {@code inner(args);}.
     * Such a method is a pure forwarder — delegation elimination can substitute the inner
     * call in-place at any call-site position, including nested expressions.
     */
    static boolean isDelegatingBody(MethodDeclaration decl) {
        Block body = decl.getBody();
        if (body == null) return false;
        @SuppressWarnings("unchecked")
        List<Statement> stmts = body.statements();
        if (stmts.size() != 1) return false;
        Statement stmt = stmts.get(0);
        if (stmt instanceof ReturnStatement ret) {
            return ret.getExpression() instanceof MethodInvocation;
        }
        if (stmt instanceof ExpressionStatement es) {
            return es.getExpression() instanceof MethodInvocation;
        }
        return false;
    }

    /**
     * True when the method body is a single {@code return} of a getter-pure expression.
     * Getter-pure extends {@link #isPure} by also allowing field reads.
     */
    static boolean isGetterBody(MethodDeclaration decl) {
        Block body = decl.getBody();
        if (body == null) return false;
        @SuppressWarnings("unchecked")
        List<Statement> stmts = body.statements();
        if (stmts.size() != 1 || !(stmts.get(0) instanceof ReturnStatement ret)) return false;
        Expression expr = ret.getExpression();
        return expr != null && isGetterExpressionPure(expr);
    }

    /**
     * True when {@code expression} is side-effect-free and can be safely substituted
     * in-place at a call site without hoisting. Extends {@link #isPure} by also
     * allowing field reads: unqualified instance fields, {@code this.field}, and
     * static fields accessed via a type name.
     */
    static boolean isGetterExpressionPure(Expression expression) {
        if (isPure(expression)) return true;
        if (expression instanceof SimpleName name) {
            return name.resolveBinding() instanceof IVariableBinding;
        }
        if (expression instanceof FieldAccess access) {
            return access.getExpression() instanceof ThisExpression te
                    && te.getQualifier() == null;
        }
        if (expression instanceof QualifiedName qn) {
            return qn.getQualifier().resolveBinding() instanceof ITypeBinding;
        }
        if (expression instanceof InfixExpression infix) {
            InfixExpression.Operator op = infix.getOperator();
            if (op == InfixExpression.Operator.CONDITIONAL_AND
                    || op == InfixExpression.Operator.CONDITIONAL_OR) return false;
            if (!isGetterExpressionPure(infix.getLeftOperand())
                    || !isGetterExpressionPure(infix.getRightOperand())) return false;
            @SuppressWarnings("unchecked")
            List<Expression> extended = infix.extendedOperands();
            for (Expression ext : extended) {
                if (!isGetterExpressionPure(ext)) return false;
            }
            return true;
        }
        if (expression instanceof CastExpression cast) {
            return isGetterExpressionPure(cast.getExpression());
        }
        if (expression instanceof PrefixExpression prefix) {
            PrefixExpression.Operator op = prefix.getOperator();
            if (op == PrefixExpression.Operator.INCREMENT
                    || op == PrefixExpression.Operator.DECREMENT) return false;
            return isGetterExpressionPure(prefix.getOperand());
        }
        if (expression instanceof ParenthesizedExpression pe) {
            return isGetterExpressionPure(pe.getExpression());
        }
        return false;
    }

    private static boolean isWriteTarget(SimpleName name) {
        ASTNode parent = name.getParent();
        if (parent instanceof Assignment assignment) {
            return assignment.getLeftHandSide() == name;
        }
        if (parent instanceof PrefixExpression prefix) {
            return prefix.getOperand() == name;
        }
        if (parent instanceof PostfixExpression postfix) {
            return postfix.getOperand() == name;
        }
        return false;
    }

    private static boolean hasUnsafeEvaluationContext(ASTNode node, ASTNode body) {
        for (ASTNode n = node.getParent(); n != null && n != body; n = n.getParent()) {
            if (n instanceof ForStatement
                    || n instanceof EnhancedForStatement
                    || n instanceof WhileStatement
                    || n instanceof DoStatement
                    || n instanceof LambdaExpression
                    || n instanceof AnonymousClassDeclaration
                    || n instanceof TypeDeclarationStatement) {
                return true;
            }
        }
        return false;
    }

    /**
     * Conservative normal-completion check used to avoid creating unreachable code.
     * Returns are treated as normal completion because the transformation rewrites
     * them to a labeled break. Constructs that are not modelled are treated as
     * non-completing so an unsafe inline is rejected instead of guessed.
     */
    static boolean canCompleteNormally(Statement statement) {
        if (statement instanceof ReturnStatement) return true;
        if (statement instanceof ThrowStatement) return false;
        if (statement instanceof BreakStatement) return false;
        if (statement instanceof ContinueStatement) return false;
        if (statement instanceof YieldStatement) return false;
        if (statement instanceof Block block) {
            @SuppressWarnings("unchecked")
            List<Statement> statements = block.statements();
            for (Statement s : statements) {
                if (!canCompleteNormally(s)) return false;
            }
            return true;
        }
        if (statement instanceof IfStatement ifs) {
            if (ifs.getElseStatement() == null) return true;
            return canCompleteNormally(ifs.getThenStatement())
                    || canCompleteNormally(ifs.getElseStatement());
        }
        if (statement instanceof WhileStatement loop) {
            if (hasUnlabeledBreak(loop.getBody())) return true;
            return !isTrue(loop.getExpression());
        }
        if (statement instanceof ForStatement loop) {
            if (hasUnlabeledBreak(loop.getBody())) return true;
            return loop.getExpression() != null && !isTrue(loop.getExpression());
        }
        if (statement instanceof EnhancedForStatement) return true;
        if (statement instanceof DoStatement loop) {
            if (hasUnlabeledBreak(loop.getBody())) return true;
            return canCompleteNormally(loop.getBody()) && !isTrue(loop.getExpression());
        }
        if (statement instanceof SwitchStatement switchStatement) {
            return !hasDefaultCase(switchStatement) || hasUnlabeledBreak(switchStatement);
        }
        if (statement instanceof SynchronizedStatement sync) {
            return canCompleteNormally(sync.getBody());
        }
        if (statement instanceof LabeledStatement labeled) {
            return canCompleteNormally(labeled.getBody());
        }
        if (statement instanceof TryStatement tr) {
            if (tr.getFinally() != null && !canCompleteNormally(tr.getFinally())) return false;
            if (canCompleteNormally(tr.getBody())) return true;
            @SuppressWarnings("unchecked")
            List<CatchClause> catches = tr.catchClauses();
            for (CatchClause clause : catches) {
                if (canCompleteNormally(clause.getBody())) return true;
            }
            return false;
        }
        if (statement instanceof ExpressionStatement) return true;
        if (statement instanceof VariableDeclarationStatement) return true;
        if (statement instanceof EmptyStatement) return true;
        if (statement instanceof AssertStatement) return true;
        if (statement instanceof TypeDeclarationStatement) return true;
        return false;
    }

    private static boolean isTrue(Expression expression) {
        return expression instanceof BooleanLiteral literal && literal.booleanValue();
    }

    private static boolean hasDefaultCase(SwitchStatement switchStatement) {
        for (Object statement : switchStatement.statements()) {
            if (statement instanceof SwitchCase switchCase && switchCase.isDefault()) {
                return true;
            }
        }
        return false;
    }

    /** True when {@code node} contains an unlabeled break that targets its own loop/switch. */
    private static boolean hasUnlabeledBreak(ASTNode node) {
        boolean[] found = {false};
        node.accept(new ASTVisitor() {
            @Override
            public boolean visit(BreakStatement statement) {
                if (statement.getLabel() == null) found[0] = true;
                return false;
            }

            @Override
            public boolean visit(WhileStatement statement) { return false; }
            @Override
            public boolean visit(DoStatement statement) { return false; }
            @Override
            public boolean visit(ForStatement statement) { return false; }
            @Override
            public boolean visit(EnhancedForStatement statement) { return false; }
            @Override
            public boolean visit(SwitchStatement statement) { return statement == node; }
            @Override
            public boolean visit(LambdaExpression statement) { return false; }
            @Override
            public boolean visit(MethodDeclaration statement) { return false; }
        });
        return found[0];
    }
}
