package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.canSubstituteArgument;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.enclosingMethod;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.isVoid;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.ownReturns;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.simpleNames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
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
                prelude.add(variableDeclaration(ast, tempName, param.getType(), argument));
            }
        }

        Block bodyCopy = (Block) ASTNode.copySubtree(ast, decl.getBody());
        renameLabels(bodyCopy, labelRenames);
        substituteNames(ast, decl.getBody(), bodyCopy,
                parameterTemps, parameterArguments, localRenames);

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
            replacement.add(variableDeclaration(ast, resultName, decl.getReturnType2(), null));
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
        InlineImports.create(cu, decl, callerType).insertInto(rewrite, cu);
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
            if (!(assignment.getLeftHandSide() instanceof SimpleName)) {
                throw reject("assignment left-hand side must be a simple name; "
                        + "receiver or index side effects would be reordered");
            }
            if (!(assignment.getParent() instanceof ExpressionStatement statement)) {
                throw reject("assignment call must be a standalone expression statement");
            }
            return new CallSite(statement, false);
        }
        throw reject("call is not the whole expression of a supported statement "
                + "(variable initializer, return, simple assignment, or expression statement)");
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
            Map<String, String> localRenames) {

        List<SimpleName> originalNames = simpleNames(originalBody);
        List<SimpleName> copiedNames = simpleNames(copiedBody);
        if (originalNames.size() != copiedNames.size()) {
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
