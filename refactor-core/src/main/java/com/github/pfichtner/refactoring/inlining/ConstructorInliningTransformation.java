package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.canSubstituteArgument;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.enclosingMethod;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.ownReturns;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.simpleNames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * Inlines a delegating constructor call ({@code this(args)}) by replacing it with the
 * body of the target constructor.
 *
 * <p>Parameters are bound to temporaries (or substituted directly when pure and used
 * once). Early {@code return} statements are rewritten to a labeled {@code break}.
 * Constructors with an explicit {@code super()} call or that themselves delegate via
 * {@code this()} are rejected.
 */
final class ConstructorInliningTransformation {

    private ConstructorInliningTransformation() {}

    /** Collects all {@code this(args)} constructor-invocation statements in the unit. */
    static List<ConstructorInvocation> collectCandidates(CompilationUnit cu) {
        List<ConstructorInvocation> result = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(ConstructorInvocation node) {
                result.add(node);
                return true;
            }
        });
        Collections.reverse(result);
        return result;
    }

    /**
     * Returns the reason the inlining is unsafe, or {@code null} when it is accepted.
     */
    static String rejectionReason(
            CompilationUnit cu, ConstructorInvocation call,
            MethodDeclaration decl, IMethodBinding binding) {

        Block body = decl.getBody();
        if (body == null) return "target constructor has no body";
        if (binding.getTypeParameters().length > 0) {
            return "generic constructor type parameters are not supported";
        }
        if (binding.isVarargs()) {
            return "varargs constructor is not supported";
        }
        // Reject constructors that delegate further via this(...)
        if (hasDelegatingConstructorInvocation(body)) {
            return "target constructor delegates to another constructor via this(); "
                    + "inline the chain one step at a time";
        }
        // Reject constructors with explicit super() — super() position management
        // in the presence of labeled breaks is not yet supported
        if (hasExplicitSuperCall(body)) {
            return "target constructor has an explicit super() call; "
                    + "super() position relative to inlined body is not supported";
        }
        // Reject checked exceptions
        IMethodBinding callerBinding = enclosingConstructorBinding(call);
        String checkedEx = firstCheckedException(cu, binding);
        if (checkedEx != null) {
            return "target constructor declares checked exception " + checkedEx;
        }
        // Reject self-delegation
        if (callerBinding != null
                && binding.getMethodDeclaration().getKey()
                        .equals(callerBinding.getMethodDeclaration().getKey())) {
            return "recursive constructor delegation";
        }
        return null;
    }

    static String apply(
            CompilationUnit cu, String source,
            ConstructorInvocation call, MethodDeclaration decl, IMethodBinding binding) {

        AST ast = cu.getAST();

        MethodDeclaration caller = enclosingMethod(call);
        if (caller == null) throw reject("constructor invocation is not inside a constructor");
        if (!(call.getParent() instanceof Block parentBlock)) {
            throw reject("this() call is not directly inside a block");
        }

        Set<String> callerNames = identifiers(caller);
        Set<String> generatedAvoid = new HashSet<>(callerNames);
        generatedAvoid.addAll(identifiers(decl.getBody()));

        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> params = decl.parameters();
        @SuppressWarnings("unchecked")
        List<Expression> args = call.arguments();
        if (params.size() != args.size()) {
            throw reject("argument count does not match the target constructor declaration");
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

        Map<String, String> localRenames = planLocalRenames(decl.getBody(), callerNames, generatedAvoid);
        Map<String, String> labelRenames = planLabelRenames(decl.getBody(), caller, generatedAvoid);

        Block bodyCopy = (Block) ASTNode.copySubtree(ast, decl.getBody());
        renameLabels(bodyCopy, labelRenames);
        substituteNames(ast, decl.getBody(), bodyCopy, parameterTemps, parameterArguments, localRenames);

        List<ReturnStatement> returns = ownReturns(bodyCopy);
        Statement inlinedBody;
        if (returns.isEmpty()) {
            // No early returns: insert body statements directly (no labeled wrapper needed).
            inlinedBody = null;
        } else {
            // Rewrite void returns to labeled break.
            String label = fresh("__inline_label", generatedAvoid);
            for (ReturnStatement ret : returns) {
                if (ret.getExpression() != null) {
                    throw reject("constructor body contains a return with a value");
                }
                replaceNode(ret, breakStatement(ast, label));
            }
            LabeledStatement labeled = ast.newLabeledStatement();
            labeled.setLabel(ast.newSimpleName(label));
            labeled.setBody(bodyCopy);
            inlinedBody = labeled;
        }

        ASTRewrite rewrite = ASTRewrite.create(ast);
        ListRewrite lr = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
        // Insert prelude (parameter temporaries) before the this() call.
        for (Statement stmt : prelude) {
            lr.insertBefore(stmt, call, null);
        }
        if (inlinedBody != null) {
            // Replace the this() call with the labeled block.
            lr.replace(call, inlinedBody, null);
        } else {
            // Insert body statements before this() then remove it.
            @SuppressWarnings("unchecked")
            List<Statement> bodyStmts = bodyCopy.statements();
            List<Statement> copies = new ArrayList<>(bodyStmts);
            for (Statement stmt : copies) {
                lr.insertBefore(stmt, call, null);
            }
            lr.remove(call, null);
        }

        return applyRewrite(source, cu, rewrite);
    }

    // -------------------------------------------------------------------------
    // Safety helpers
    // -------------------------------------------------------------------------

    private static boolean hasDelegatingConstructorInvocation(Block body) {
        boolean[] found = {false};
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(ConstructorInvocation node) {
                found[0] = true;
                return false;
            }
            @Override public boolean visit(LambdaExpression node) { return false; }
            @Override public boolean visit(MethodDeclaration node) { return false; }
        });
        return found[0];
    }

    private static boolean hasExplicitSuperCall(Block body) {
        boolean[] found = {false};
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(SuperConstructorInvocation node) {
                found[0] = true;
                return false;
            }
            @Override public boolean visit(LambdaExpression node) { return false; }
            @Override public boolean visit(MethodDeclaration node) { return false; }
        });
        return found[0];
    }

    private static IMethodBinding enclosingConstructorBinding(ConstructorInvocation call) {
        MethodDeclaration caller = enclosingMethod(call);
        if (caller == null) return null;
        return caller.resolveBinding();
    }

    private static String firstCheckedException(CompilationUnit cu, IMethodBinding binding) {
        IMethodBinding[] exceptions = new IMethodBinding[0];
        org.eclipse.jdt.core.dom.ITypeBinding[] exTypes = binding.getExceptionTypes();
        if (exTypes.length == 0) return null;
        AST ast = cu.getAST();
        org.eclipse.jdt.core.dom.ITypeBinding throwable = ast.resolveWellKnownType("java.lang.Throwable");
        org.eclipse.jdt.core.dom.ITypeBinding runtime = ast.resolveWellKnownType("java.lang.RuntimeException");
        org.eclipse.jdt.core.dom.ITypeBinding error = ast.resolveWellKnownType("java.lang.Error");
        for (org.eclipse.jdt.core.dom.ITypeBinding ex : exTypes) {
            org.eclipse.jdt.core.dom.ITypeBinding erasure = ex.getErasure();
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

    // -------------------------------------------------------------------------
    // Name / rename helpers (mirrors InlineMethodTransformation)
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

    private static Set<String> labelNames(ASTNode body) {
        Set<String> result = new HashSet<>();
        body.accept(new ASTVisitor() {
            @Override
            public boolean visit(LabeledStatement statement) {
                result.add(statement.getLabel().getIdentifier());
                return true;
            }
            @Override public boolean visit(LambdaExpression statement) { return false; }
            @Override public boolean visit(MethodDeclaration statement) { return false; }
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
            @Override public boolean visit(LambdaExpression statement) { return false; }
            @Override public boolean visit(MethodDeclaration statement) { return false; }
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
            throw new IllegalStateException("Copied constructor body does not mirror the original");
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
            String paramTemp = parameterTemps.get(variable.getKey());
            if (paramTemp != null) {
                replaceNode(copiedNames.get(i), ast.newSimpleName(paramTemp));
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
    // AST construction / mutation
    // -------------------------------------------------------------------------

    private static VariableDeclarationStatement variableDeclaration(
            AST ast, String name, org.eclipse.jdt.core.dom.Type type, Expression initializer) {

        VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
        fragment.setName(ast.newSimpleName(name));
        if (initializer != null) {
            fragment.setInitializer((Expression) ASTNode.copySubtree(ast, initializer));
        }
        VariableDeclarationStatement statement = ast.newVariableDeclarationStatement(fragment);
        statement.setType((org.eclipse.jdt.core.dom.Type) ASTNode.copySubtree(ast, type));
        return statement;
    }

    private static BreakStatement breakStatement(AST ast, String label) {
        BreakStatement statement = ast.newBreakStatement();
        statement.setLabel(ast.newSimpleName(label));
        return statement;
    }

    @SuppressWarnings("unchecked")
    private static void replaceNode(ASTNode oldNode, ASTNode replacement) {
        ASTNode parent = oldNode.getParent();
        if (parent == null) throw new IllegalStateException("Cannot replace a detached AST node");
        for (org.eclipse.jdt.core.dom.StructuralPropertyDescriptor property
                : (List<org.eclipse.jdt.core.dom.StructuralPropertyDescriptor>)
                        parent.structuralPropertiesForType()) {
            Object value = parent.getStructuralProperty(property);
            if (value == oldNode) {
                parent.setStructuralProperty(property, replacement);
                return;
            }
            if (property instanceof org.eclipse.jdt.core.dom.ChildListPropertyDescriptor
                    && value instanceof List<?>) {
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

    private static String applyRewrite(String source, CompilationUnit cu, ASTRewrite rewrite) {
        try {
            Document document = new Document(source);
            TextEdit edits = rewrite.rewriteAST(document, null);
            edits.apply(document);
            return document.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply constructor inline edits", e);
        }
    }

    private static InlineRejectedException reject(String reason) {
        return new InlineRejectedException(reason);
    }
}
