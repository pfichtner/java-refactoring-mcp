package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.canSubstituteArgument;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.enclosingMethod;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.isPure;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.isVoid;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.ownReturns;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.simpleNames;
import static org.eclipse.jdt.core.dom.Modifier.isStatic;

import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * One method-call inline transformation.
 *
 * <p>Arguments are bound to synthetic locals first, so each argument is
 * evaluated exactly once, in source order. A pure argument used exactly once
 * is substituted directly instead. The method body is then copied, local
 * variables are renamed only when they would collide with the caller,
 * parameters are substituted, and returns are rewritten to assignments plus a
 * labeled break. Value-returning calls are hoisted into a synthetic result
 * variable.
 */
final class InlineMethodTransformation {

    private InlineMethodTransformation() {}

    static String apply(
            CompilationUnit cu, String source, MethodInvocation call,
            MethodDeclaration decl, IMethodBinding binding) {

        AST ast = cu.getAST();
        boolean valueReturning = !isVoid(binding);

        CallSite callSite = locateCallSite(call, valueReturning);
        if (!callSite.replaceWholeStatement && !(callSite.anchor.getParent() instanceof Block)) {
            throw reject("call site must be a direct child of a block statement");
        }

        MethodDeclaration caller = enclosingMethod(call);
        if (caller == null) throw reject("call is not inside a method body");

        Set<String> callerNames = identifiers(caller);
        Set<String> generatedAvoid = new HashSet<>(callerNames);
        generatedAvoid.addAll(identifiers(decl.getBody()));

        Map<String, ITypeBinding> typeVarSubst = buildTypeVarSubstitution(binding.getDeclaringClass());

        Expression receiver = call.getExpression();
        boolean instanceMethod = !isStatic(binding.getModifiers());
        String receiverName = instanceMethod && receiver != null
                        && !(receiver instanceof ThisExpression)
                ? fresh("__inline_receiver", generatedAvoid)
                : null;

        Map<String, String> localRenames = planLocalRenames(decl.getBody(), callerNames, generatedAvoid);
        Map<String, String> labelRenames = planLabelRenames(decl.getBody(), caller, generatedAvoid);

        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> params = decl.parameters();
        @SuppressWarnings("unchecked")
        List<Expression> args = call.arguments();
        if (params.size() != args.size()) {
            throw reject("argument count does not match the target method declaration");
        }

        Map<String, String> parameterTemps = new HashMap<>();
        Map<String, Expression> parameterArguments = new HashMap<>();
        List<Statement> prelude = new ArrayList<>();
        if (receiverName != null) {
            prelude.add(variableDeclaration(ast, receiverName,
                    typeBindingToAstType(ast, binding.getDeclaringClass()), receiver));
        }
        for (int i = 0; i < params.size(); i++) {
            SingleVariableDeclaration param = params.get(i);
            IVariableBinding paramBinding = param.resolveBinding();
            if (paramBinding == null) {
                throw reject("cannot resolve parameter '" + param.getName() + "'");
            }
            Expression argument = args.get(i);
            String key = paramBinding.getKey();
            if (canSubstituteArgument(argument, paramBinding, decl.getBody())) {
                parameterArguments.put(key, argument);
            } else {
                String tempName = fresh("__inline_" + paramBinding.getName(), generatedAvoid);
                parameterTemps.put(key, tempName);
                Type paramType = typeVarSubst.isEmpty()
                        ? param.getType()
                        : typeBindingToAstType(ast, binding.getParameterTypes()[i]);
                prelude.add(variableDeclaration(ast, tempName, paramType, argument));
            }
        }
        if (receiverName != null) {
            prelude.add(nullCheck(ast, receiverName));
        }

        Block bodyCopy = (Block) ASTNode.copySubtree(ast, decl.getBody());
        renameLabels(bodyCopy, labelRenames);
        substituteNames(ast, decl.getBody(), bodyCopy,
                parameterTemps, parameterArguments, localRenames, receiverName);
        if (!typeVarSubst.isEmpty()) {
            substituteTypeVariables(decl.getBody(), bodyCopy, typeVarSubst);
        }

        List<ReturnStatement> returns = ownReturns(bodyCopy);
        String resultName = null;
        if (valueReturning) {
            if (returns.isEmpty()) {
                throw reject("value-returning method has no return statement");
            }
            resultName = fresh("__inline_result", generatedAvoid);
        }
        String label = returns.isEmpty() ? null : fresh("__inline_label", generatedAvoid);
        rewriteReturns(ast, returns, valueReturning, resultName, label);

        Statement inlinedBody = bodyCopy;
        if (label != null) {
            LabeledStatement labeled = ast.newLabeledStatement();
            labeled.setLabel(ast.newSimpleName(label));
            labeled.setBody(bodyCopy);
            inlinedBody = labeled;
        }

        List<Statement> replacement = new ArrayList<>(prelude);
        if (resultName != null) {
            Type resultType = typeVarSubst.isEmpty()
                    ? decl.getReturnType2()
                    : typeBindingToAstType(ast, binding.getReturnType());
            replacement.add(variableDeclaration(ast, resultName, resultType, null));
        }
        replacement.add(inlinedBody);

        ASTRewrite rewrite = ASTRewrite.create(ast);
        if (callSite.replaceWholeStatement) {
            Block outer = ast.newBlock();
            for (Statement statement : replacement) outer.statements().add(statement);
            rewrite.replace(callSite.anchor, outer, null);
        } else {
            @SuppressWarnings("unchecked")
            ListRewrite list = rewrite.getListRewrite(
                    callSite.anchor.getParent(), Block.STATEMENTS_PROPERTY);
            for (Statement statement : replacement) {
                list.insertBefore(statement, callSite.anchor, null);
            }
            rewrite.replace(call, ast.newSimpleName(resultName), null);
        }

        IMethodBinding callerBinding = caller.resolveBinding();
        ITypeBinding callerType = callerBinding == null ? null : callerBinding.getDeclaringClass();
        InlineImports.create(cu, decl, callerType, new ArrayList<>(typeVarSubst.values()))
                .insertInto(rewrite, cu);
        return applyRewrite(cu, source, rewrite);
    }

    // -------------------------------------------------------------------------
    // Call-site analysis
    // -------------------------------------------------------------------------

    private record CallSite(Statement anchor, boolean replaceWholeStatement) {}

    private static CallSite locateCallSite(MethodInvocation call, boolean valueReturning) {
        ASTNode parent = call.getParent();

        if (!valueReturning) {
            if (parent instanceof ExpressionStatement statement
                    && statement.getExpression() == call) {
                return new CallSite(statement, true);
            }
            throw reject("void method call must be a standalone expression statement");
        }

        if (parent instanceof ExpressionStatement statement
                && statement.getExpression() == call) {
            return new CallSite(statement, true);
        }
        if (parent instanceof VariableDeclarationFragment fragment
                && fragment.getInitializer() == call
                && fragment.getParent() instanceof VariableDeclarationStatement declaration
                && declaration.fragments().size() == 1) {
            return new CallSite(declaration, false);
        }
        if (parent instanceof ReturnStatement statement
                && statement.getExpression() == call) {
            return new CallSite(statement, false);
        }
        if (parent instanceof Assignment assignment
                && assignment.getRightHandSide() == call) {
            if (!isSideEffectFreeAssignmentTarget(assignment.getLeftHandSide())) {
                throw reject("assignment left-hand side has side effects or may be affected by hoisting; "
                        + "receiver or index evaluation would be reordered");
            }
            if (!(assignment.getParent() instanceof ExpressionStatement statement)) {
                throw reject("assignment call must be a standalone expression statement");
            }
            return new CallSite(statement, false);
        }
        return findNestedCallSite(call);
    }

    /**
     * Accepts a value-returning call nested inside a larger expression when every
     * sub-expression evaluated before the call (in Java left-to-right order) is
     * side-effect-free, so hoisting the body before the containing statement cannot
     * reorder observable effects.
     */
    private static CallSite findNestedCallSite(MethodInvocation call) {
        ASTNode child = call;
        ASTNode parent = call.getParent();

        while (parent != null) {
            if (parent instanceof LambdaExpression
                    || parent instanceof AnonymousClassDeclaration
                    || parent instanceof MethodDeclaration) {
                break;
            }

            if (parent instanceof Assignment assignment
                    && child == assignment.getLeftHandSide()) {
                break;
            }

            if (parent instanceof Expression expr) {
                String reason = evaluationOrderViolation(child, expr);
                if (reason != null) throw reject(reason);
                child = parent;
                parent = parent.getParent();
                continue;
            }

            if (parent instanceof VariableDeclarationFragment fragment
                    && fragment.getInitializer() == child
                    && fragment.getParent() instanceof VariableDeclarationStatement vds
                    && vds.fragments().size() == 1) {
                if (!(vds.getParent() instanceof Block)) {
                    throw reject("nested expression: containing declaration is not directly in a block");
                }
                return new CallSite(vds, false);
            }

            if (parent instanceof ReturnStatement ret && ret.getExpression() == child) {
                if (!(ret.getParent() instanceof Block)) {
                    throw reject("nested expression: containing return is not directly in a block");
                }
                return new CallSite(ret, false);
            }

            if (parent instanceof ExpressionStatement es && es.getExpression() == child) {
                if (!(es.getParent() instanceof Block)) {
                    throw reject("nested expression: containing statement is not directly in a block");
                }
                return new CallSite(es, false);
            }

            if (parent instanceof IfStatement ifStmt && ifStmt.getExpression() == child) {
                if (!(ifStmt.getParent() instanceof Block)) {
                    throw reject("nested expression: containing if-statement is not directly in a block");
                }
                return new CallSite(ifStmt, false);
            }

            if (parent instanceof ThrowStatement throwStmt && throwStmt.getExpression() == child) {
                if (!(throwStmt.getParent() instanceof Block)) {
                    throw reject("nested expression: containing throw statement is not directly in a block");
                }
                return new CallSite(throwStmt, false);
            }

            if (parent instanceof SwitchStatement switchStmt && switchStmt.getExpression() == child) {
                if (!(switchStmt.getParent() instanceof Block)) {
                    throw reject("nested expression: containing switch statement is not directly in a block");
                }
                return new CallSite(switchStmt, false);
            }

            if (parent instanceof EnhancedForStatement forStmt && forStmt.getExpression() == child) {
                if (!(forStmt.getParent() instanceof Block)) {
                    throw reject("nested expression: containing enhanced-for statement is not directly in a block");
                }
                return new CallSite(forStmt, false);
            }

            if (parent instanceof VariableDeclarationFragment fragment
                    && fragment.getInitializer() == child
                    && fragment.getParent() instanceof VariableDeclarationExpression vde
                    && vde.fragments().size() == 1
                    && vde.getParent() instanceof ForStatement regularForStmt
                    && regularForStmt.initializers().size() == 1
                    && regularForStmt.getParent() instanceof Block) {
                return new CallSite(regularForStmt, false);
            }

            break;
        }

        throw reject("call is not the whole expression of a supported statement "
                + "(variable initializer, return, simple assignment, expression statement, "
                + "if condition, throw expression, switch selector, enhanced-for iterable, "
                + "array index, or for-loop initializer)");
    }

    /**
     * Returns a rejection reason if hoisting the body of the call before the
     * containing statement would reorder an observable side effect that {@code child}
     * (an expression on the path from the call to the statement) has relative to its
     * parent expression.  Returns {@code null} when no reordering occurs.
     */
    private static String evaluationOrderViolation(ASTNode child, Expression parent) {
        if (parent instanceof InfixExpression infix) {
            if (child == infix.getRightOperand()) {
                InfixExpression.Operator op = infix.getOperator();
                if (op == InfixExpression.Operator.CONDITIONAL_AND
                        || op == InfixExpression.Operator.CONDITIONAL_OR) {
                    return "nested call in right operand of " + op
                            + ": short-circuit may skip the call; hoisting would always evaluate it";
                }
                if (!isPure(infix.getLeftOperand())) {
                    return "hoisting before statement would reorder evaluation: "
                            + "left operand of `" + op + "` has side effects";
                }
            }
            @SuppressWarnings("unchecked")
            List<Expression> extended = infix.extendedOperands();
            int extIdx = extended.indexOf(child);
            if (extIdx >= 0) {
                if (!isPure(infix.getLeftOperand()) || !isPure(infix.getRightOperand())) {
                    return "hoisting before statement would reorder evaluation: infix operand has side effects";
                }
                for (int i = 0; i < extIdx; i++) {
                    if (!isPure(extended.get(i))) {
                        return "hoisting before statement would reorder evaluation: infix operand has side effects";
                    }
                }
            }
            return null;
        }

        if (parent instanceof MethodInvocation outer) {
            if (child != outer.getExpression()) {
                @SuppressWarnings("unchecked")
                List<Expression> args = outer.arguments();
                int argIdx = args.indexOf(child);
                if (argIdx >= 0) {
                    Expression receiver = outer.getExpression();
                    if (receiver != null && !isPure(receiver)) {
                        return "hoisting before statement would reorder evaluation: "
                                + "receiver of outer call has side effects";
                    }
                    for (int i = 0; i < argIdx; i++) {
                        if (!isPure(args.get(i))) {
                            return "hoisting before statement would reorder evaluation: "
                                    + "earlier argument of outer call has side effects";
                        }
                    }
                }
            }
            return null;
        }

        if (parent instanceof Assignment assignment && child == assignment.getRightHandSide()) {
            if (!isSideEffectFreeAssignmentTarget(assignment.getLeftHandSide())) {
                return "nested expression in assignment RHS: left-hand side has side effects";
            }
            return null;
        }

        if (parent instanceof CastExpression
                || parent instanceof ParenthesizedExpression) {
            return null;
        }

        if (parent instanceof PrefixExpression prefix) {
            PrefixExpression.Operator op = prefix.getOperator();
            if (op == PrefixExpression.Operator.INCREMENT
                    || op == PrefixExpression.Operator.DECREMENT) {
                return "nested call under prefix " + op + ": not supported";
            }
            return null;
        }

        if (parent instanceof ConditionalExpression ternary) {
            if (child == ternary.getThenExpression() || child == ternary.getElseExpression()) {
                return "nested call in conditional expression branch: "
                        + "branch may not be evaluated; hoisting would always evaluate it";
            }
            // child is the condition: always evaluated, safe to hoist
            return null;
        }

        if (parent instanceof ClassInstanceCreation creation) {
            Expression outer = creation.getExpression();
            if (outer != null && !isPure(outer)) {
                return "hoisting before statement would reorder evaluation: "
                        + "outer instance of constructor call has side effects";
            }
            @SuppressWarnings("unchecked")
            List<Expression> args = creation.arguments();
            int argIdx = args.indexOf(child);
            if (argIdx >= 0) {
                for (int i = 0; i < argIdx; i++) {
                    if (!isPure(args.get(i))) {
                        return "hoisting before statement would reorder evaluation: "
                                + "earlier argument of constructor call has side effects";
                    }
                }
            }
            return null;
        }

        if (parent instanceof ArrayCreation creation) {
            @SuppressWarnings("unchecked")
            List<Expression> dims = creation.dimensions();
            int dimIdx = dims.indexOf(child);
            if (dimIdx >= 0) {
                for (int i = 0; i < dimIdx; i++) {
                    if (!isPure(dims.get(i))) {
                        return "hoisting before statement would reorder evaluation: "
                                + "earlier array dimension has side effects";
                    }
                }
            }
            return null;
        }

        if (parent instanceof ArrayAccess access) {
            if (child == access.getIndex() && !isPure(access.getArray())) {
                return "hoisting before statement would reorder evaluation: "
                        + "array expression has side effects";
            }
            return null;
        }

        return "nested call position not supported (call is in "
                + parent.getClass().getSimpleName() + ")";
    }


    private static boolean isSideEffectFreeAssignmentTarget(Expression lhs) {
        if (lhs instanceof SimpleName) return true;
        if (lhs instanceof FieldAccess access) {
            return access.getExpression() instanceof ThisExpression thisExpression
                    && thisExpression.getQualifier() == null;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Name handling
    // -------------------------------------------------------------------------

    private static Set<String> identifiers(ASTNode node) {
        Set<String> result = new HashSet<>();
        for (SimpleName name : simpleNames(node)) {
            result.add(name.getIdentifier());
        }
        return result;
    }

    private static Map<String, String> planLocalRenames(
            ASTNode body, Set<String> callerNames, Set<String> generatedAvoid) {

        Map<String, String> renames = new HashMap<>();
        for (SimpleName name : simpleNames(body)) {
            if (!(name.resolveBinding() instanceof IVariableBinding variable)) continue;
            if (variable.isField() || variable.isEnumConstant() || variable.isParameter()) continue;
            if (!callerNames.contains(variable.getName())) continue;
            renames.computeIfAbsent(variable.getKey(),
                    key -> fresh("__inline_" + variable.getName(), generatedAvoid));
        }
        return renames;
    }

    private static Map<String, String> planLabelRenames(
            ASTNode calleeBody, MethodDeclaration caller, Set<String> generatedAvoid) {

        Set<String> callerLabels = labelNames(caller.getBody());
        Map<String, String> renames = new HashMap<>();
        for (String label : labelNames(calleeBody)) {
            if (callerLabels.contains(label)) {
                renames.put(label, fresh("__inline_label_" + label, generatedAvoid));
            }
        }
        return renames;
    }

    /** Label declarations in this method body, excluding nested lambdas and local methods. */
    private static Set<String> labelNames(ASTNode body) {
        Set<String> result = new HashSet<>();
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(LabeledStatement statement) {
                result.add(statement.getLabel().getIdentifier());
                return true;
            }

            @Override
            public boolean visit(LambdaExpression statement) { return false; }
            @Override
            public boolean visit(MethodDeclaration statement) { return false; }
        });
        return result;
    }

    private static void renameLabels(ASTNode body, Map<String, String> renames) {
        if (renames.isEmpty()) return;
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(LabeledStatement statement) {
                renameLabelReference(statement.getLabel(), renames);
                return true;
            }

            @Override
            public boolean visit(BreakStatement statement) {
                renameLabelReference(statement.getLabel(), renames);
                return true;
            }

            @Override
            public boolean visit(ContinueStatement statement) {
                renameLabelReference(statement.getLabel(), renames);
                return true;
            }

            @Override
            public boolean visit(LambdaExpression statement) { return false; }
            @Override
            public boolean visit(MethodDeclaration statement) { return false; }
        });
    }

    private static void renameLabelReference(SimpleName label, Map<String, String> renames) {
        if (label == null) return;
        String renamed = renames.get(label.getIdentifier());
        if (renamed != null) label.setIdentifier(renamed);
    }

    private static void substituteNames(
            AST ast, ASTNode originalBody, ASTNode copiedBody,
            Map<String, String> parameterTemps,
            Map<String, Expression> parameterArguments,
            Map<String, String> localRenames,
            String receiverName) {

        List<SimpleName> originalNames = simpleNames(originalBody);
        List<SimpleName> copiedNames = simpleNames(copiedBody);
        List<ThisExpression> originalThis = thisExpressions(originalBody);
        List<ThisExpression> copiedThis = thisExpressions(copiedBody);
        if (originalNames.size() != copiedNames.size()
                || originalThis.size() != copiedThis.size()) {
            throw new IllegalStateException("Copied method body does not mirror the original body");
        }

        for (int i = 0; i < originalNames.size(); i++) {
            if (!(originalNames.get(i).resolveBinding() instanceof IVariableBinding variable)) {
                continue;
            }
            Expression argument = parameterArguments.get(variable.getKey());
            if (argument != null) {
                replaceNode(copiedNames.get(i), ASTNode.copySubtree(ast, argument));
                continue;
            }
            String parameterTemp = parameterTemps.get(variable.getKey());
            if (parameterTemp != null) {
                replaceNode(copiedNames.get(i), ast.newSimpleName(parameterTemp));
                continue;
            }
            String renamed = localRenames.get(variable.getKey());
            if (renamed != null) {
                copiedNames.get(i).setIdentifier(renamed);
                continue;
            }
            if (receiverName != null
                    && variable.isField()
                    && !variable.isEnumConstant()
                    && !isStatic(variable.getModifiers())
                    && isUnqualifiedFieldName(originalNames.get(i))) {
                FieldAccess access = ast.newFieldAccess();
                access.setExpression(ast.newSimpleName(receiverName));
                access.setName(ast.newSimpleName(variable.getName()));
                replaceNode(copiedNames.get(i), access);
            }
        }

        for (int i = 0; i < originalNames.size(); i++) {
            if (receiverName != null
                    && originalNames.get(i).resolveBinding() instanceof IMethodBinding method
                    && !isStatic(method.getModifiers())
                    && isUnqualifiedMethodName(originalNames.get(i))) {
                MethodInvocation invocation = (MethodInvocation) copiedNames.get(i).getParent();
                invocation.setExpression(ast.newSimpleName(receiverName));
            }
        }

        if (receiverName != null) {
            for (int i = 0; i < originalThis.size(); i++) {
                if (originalThis.get(i).getQualifier() == null) {
                    replaceNode(copiedThis.get(i), ast.newSimpleName(receiverName));
                }
            }
        }
    }

    private static String fresh(String base, Set<String> used) {
        String candidate = base;
        int suffix = 0;
        while (!used.add(candidate)) {
            candidate = base + (++suffix);
        }
        return candidate;
    }

    // -------------------------------------------------------------------------
    // Return rewriting
    // -------------------------------------------------------------------------

    private static void rewriteReturns(
            AST ast, List<ReturnStatement> returns, boolean valueReturning,
            String resultName, String label) {

        for (ReturnStatement statement : returns) {
            if (valueReturning) {
                Expression value = statement.getExpression();
                if (value == null) throw reject("value-returning method has a bare return");
                Assignment assignment = ast.newAssignment();
                assignment.setLeftHandSide(ast.newSimpleName(resultName));
                assignment.setOperator(Assignment.Operator.ASSIGN);
                assignment.setRightHandSide((Expression) ASTNode.copySubtree(ast, value));
                Block replacement = ast.newBlock();
                replacement.statements().add(ast.newExpressionStatement(assignment));
                replacement.statements().add(breakStatement(ast, label));
                replaceNode(statement, replacement);
            } else {
                if (statement.getExpression() != null) {
                    throw reject("void method contains a return with a value");
                }
                replaceNode(statement, breakStatement(ast, label));
            }
        }
    }

    private static BreakStatement breakStatement(AST ast, String label) {
        BreakStatement statement = ast.newBreakStatement();
        statement.setLabel(ast.newSimpleName(label));
        return statement;
    }

    private static boolean isUnqualifiedFieldName(SimpleName name) {
        ASTNode parent = name.getParent();
        if (parent instanceof FieldAccess access) {
            return access.getName() != name;
        }
        return !(parent instanceof QualifiedName) && !(parent instanceof SuperFieldAccess);
    }

    private static boolean isUnqualifiedMethodName(SimpleName name) {
        ASTNode parent = name.getParent();
        return parent instanceof MethodInvocation invocation
                && invocation.getName() == name
                && invocation.getExpression() == null;
    }

    private static List<ThisExpression> thisExpressions(ASTNode body) {
        List<ThisExpression> result = new ArrayList<>();
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(ThisExpression node) {
                result.add(node);
                return true;
            }
        });
        return result;
    }

    private static Map<String, ITypeBinding> buildTypeVarSubstitution(ITypeBinding declaringType) {
        ITypeBinding[] typeParams = declaringType.getTypeDeclaration().getTypeParameters();
        ITypeBinding[] typeArgs = declaringType.getTypeArguments();
        if (typeParams.length == 0 || typeArgs.length != typeParams.length) {
            return Map.of();
        }
        Map<String, ITypeBinding> map = new HashMap<>();
        for (int i = 0; i < typeParams.length; i++) {
            map.put(typeParams[i].getName(), typeArgs[i]);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Type typeBindingToAstType(AST ast, ITypeBinding binding) {
        if (binding.isPrimitive()) {
            return ast.newPrimitiveType(PrimitiveType.toCode(binding.getName()));
        }
        if (binding.isArray()) {
            return ast.newArrayType(typeBindingToAstType(ast, binding.getElementType()));
        }
        if (binding.isParameterizedType()) {
            ParameterizedType pt = ast.newParameterizedType(
                    ast.newSimpleType(ast.newSimpleName(binding.getErasure().getName())));
            for (ITypeBinding arg : binding.getTypeArguments()) {
                pt.typeArguments().add(typeBindingToAstType(ast, arg));
            }
            return pt;
        }
        return ast.newSimpleType(ast.newSimpleName(binding.getName()));
    }

    static void substituteTypeVariables(
            ASTNode originalBody, ASTNode copiedBody, Map<String, ITypeBinding> typeVarSubst) {
        List<SimpleName> originalNames = simpleNames(originalBody);
        List<SimpleName> copiedNames = simpleNames(copiedBody);
        for (int i = 0; i < originalNames.size(); i++) {
            IBinding b = originalNames.get(i).resolveBinding();
            if (b instanceof ITypeBinding tb && tb.isTypeVariable()) {
                ITypeBinding substituted = typeVarSubst.get(tb.getName());
                if (substituted != null) {
                    SimpleName copiedName = copiedNames.get(i);
                    if (substituted.isParameterizedType() || substituted.isArray()) {
                        ASTNode parent = copiedName.getParent();
                        if (parent instanceof SimpleType simpleType) {
                            Type newType = typeBindingToAstType(copiedBody.getAST(), substituted);
                            replaceNode(simpleType, newType);
                        }
                    } else {
                        copiedName.setIdentifier(substituted.getName());
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Statement nullCheck(AST ast, String receiverName) {
        MethodInvocation invocation = ast.newMethodInvocation();
        invocation.setExpression(ast.newName("java.util.Objects"));
        invocation.setName(ast.newSimpleName("requireNonNull"));
        invocation.arguments().add(ast.newSimpleName(receiverName));
        return ast.newExpressionStatement(invocation);
    }

    // -------------------------------------------------------------------------
    // AST construction / mutation
    // -------------------------------------------------------------------------

    private static VariableDeclarationStatement variableDeclaration(
            AST ast, String name, Type type, Expression initializer) {

        VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
        fragment.setName(ast.newSimpleName(name));
        if (initializer != null) {
            fragment.setInitializer((Expression) ASTNode.copySubtree(ast, initializer));
        }
        VariableDeclarationStatement statement =
                ast.newVariableDeclarationStatement(fragment);
        statement.setType((Type) ASTNode.copySubtree(ast, type));
        return statement;
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
        throw new IllegalStateException("Cannot find child property for " + oldNode.getClass().getSimpleName());
    }

    private static String applyRewrite(
            CompilationUnit cu, String source, ASTRewrite rewrite) {
        try {
            Document document = new Document(source);
            TextEdit edits = rewrite.rewriteAST(document, null);
            edits.apply(document);
            return document.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply inline edits", e);
        }
    }

    private static InlineRejectedException reject(String reason) {
        return new InlineRejectedException(reason);
    }
}
