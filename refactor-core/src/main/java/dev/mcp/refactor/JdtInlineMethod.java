package dev.mcp.refactor;

import org.eclipse.jdt.core.dom.*;

import java.util.*;

/**
 * Headless Inline Method refactoring using JDT ASTParser.
 *
 * <p>Replaces a method call with the body of the called method, substituting
 * formal parameters with actual argument expressions.
 *
 * <p>Supported cases (single file):
 * <ul>
 *   <li>Void method called as a statement (zero or more parameters)</li>
 *   <li>Value-returning method with a single {@code return} statement, used
 *       as an expression (in a declaration, return, or argument)</li>
 * </ul>
 *
 * <p>Precondition failures (diagnostic, no file modified):
 * <ul>
 *   <li>Method declaration not found in the same file</li>
 *   <li>Method is abstract or native (no body)</li>
 *   <li>Void method body contains a {@code return} statement</li>
 *   <li>Value method has more than one statement in its body</li>
 *   <li>Recursive call</li>
 * </ul>
 */
public class JdtInlineMethod {

    /**
     * Inlines the method call whose name overlaps with {@code offset}.
     *
     * @param source   full source text
     * @param unitName file name for binding resolution (e.g. {@code "Foo.java"})
     * @param offset   character offset of any character within the call's method name
     * @return rewritten source with the call replaced by the method body
     */
    public static String inlineMethod(String source, String unitName, int offset) {
        CompilationUnit cu = parse(source, unitName);

        MethodInvocation call = findMethodInvocation(cu, offset);
        IMethodBinding binding = call.resolveMethodBinding();
        if (binding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve method call at offset " + offset + ".");
        }

        MethodDeclaration decl = findMethodDeclaration(cu, binding);
        if (decl == null) {
            throw new IllegalArgumentException(
                    "Method '" + binding.getName()
                    + "' is not declared in this file. Only single-file inline is supported.");
        }
        if (decl == findEnclosingMethod(call)) {
            throw new IllegalArgumentException(
                    "Cannot inline a recursive call.");
        }

        Block body = decl.getBody();
        if (body == null) {
            throw new IllegalArgumentException(
                    "Cannot inline '" + binding.getName()
                    + "': method is abstract or native.");
        }

        @SuppressWarnings("unchecked")
        List<Statement> stmts = body.statements();
        if (stmts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot inline '" + binding.getName() + "': method body is empty.");
        }

        // Build parameter → argument text map
        Map<String, String> paramMap = buildParamMap(decl, call, source);

        // Determine call context and inline accordingly
        ASTNode callParent = call.getParent();
        if (callParent instanceof ExpressionStatement callStmt) {
            return inlineVoidCall(source, cu, callStmt, decl, stmts, paramMap);
        } else {
            return inlineValueCall(source, cu, call, decl, stmts, paramMap);
        }
    }

    // -------------------------------------------------------------------------
    // Void call (ExpressionStatement context)
    // -------------------------------------------------------------------------

    private static String inlineVoidCall(
            String source, CompilationUnit cu,
            ExpressionStatement callStmt,
            MethodDeclaration decl,
            List<Statement> stmts,
            Map<String, String> paramMap) {

        for (Statement s : stmts) {
            if (s instanceof ReturnStatement) {
                throw new IllegalArgumentException(
                        "Cannot inline void method: body contains a return statement.");
            }
        }

        // Call site indentation
        int stmtStart = callStmt.getStartPosition();
        int lineStart = stmtStart;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
        String indent = source.substring(lineStart, stmtStart);

        // Generate substituted statement texts
        StringBuilder inlined = new StringBuilder();
        for (Statement s : stmts) {
            String sText = substituteInRange(source, s.getStartPosition(),
                    s.getStartPosition() + s.getLength(), paramMap, cu);
            inlined.append(indent).append(sText.strip()).append("\n");
        }
        // Remove trailing newline added by the loop
        if (!inlined.isEmpty() && inlined.charAt(inlined.length() - 1) == '\n') {
            inlined.deleteCharAt(inlined.length() - 1);
        }

        int callStmtEnd = callStmt.getStartPosition() + callStmt.getLength();
        return source.substring(0, lineStart) + inlined + source.substring(callStmtEnd);
    }

    // -------------------------------------------------------------------------
    // Value call (used as an expression)
    // -------------------------------------------------------------------------

    private static String inlineValueCall(
            String source, CompilationUnit cu,
            MethodInvocation call,
            MethodDeclaration decl,
            List<Statement> stmts,
            Map<String, String> paramMap) {

        if (stmts.size() != 1 || !(stmts.get(0) instanceof ReturnStatement rs)) {
            throw new IllegalArgumentException(
                    "Can only inline a method used as an expression if the method "
                    + "has exactly one statement and it is a return statement.");
        }
        Expression returnExpr = rs.getExpression();
        if (returnExpr == null) {
            throw new IllegalArgumentException(
                    "Return statement has no expression.");
        }

        String exprText = substituteInRange(
                source, returnExpr.getStartPosition(),
                returnExpr.getStartPosition() + returnExpr.getLength(),
                paramMap, cu);

        // Wrap in parens when inlining into a complex context
        boolean needsParens = !(call.getParent() instanceof VariableDeclarationFragment
                || call.getParent() instanceof ReturnStatement
                || call.getParent() instanceof ExpressionStatement
                || call.getParent() instanceof Assignment);
        String replacement = needsParens ? "(" + exprText + ")" : exprText;

        int callStart = call.getStartPosition();
        int callEnd   = callStart + call.getLength();
        return source.substring(0, callStart) + replacement + source.substring(callEnd);
    }

    // -------------------------------------------------------------------------
    // Parameter substitution
    // -------------------------------------------------------------------------

    private static Map<String, String> buildParamMap(
            MethodDeclaration decl, MethodInvocation call, String source) {
        @SuppressWarnings("unchecked") List<SingleVariableDeclaration> params = decl.parameters();
        @SuppressWarnings("unchecked") List<Expression> args = call.arguments();
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < params.size(); i++) {
            IVariableBinding b = params.get(i).resolveBinding();
            if (b != null) {
                Expression arg = args.get(i);
                map.put(b.getKey(), source.substring(
                        arg.getStartPosition(), arg.getStartPosition() + arg.getLength()));
            }
        }
        return map;
    }

    /** Returns the text of {@code source[start..end)} with parameter names replaced. */
    private static String substituteInRange(
            String source, int start, int end,
            Map<String, String> paramMap, CompilationUnit cu) {
        if (paramMap.isEmpty()) return source.substring(start, end);

        // Collect param usages in [start, end)
        List<Object[]> reps = new ArrayList<>(); // [absStart, absEnd, argText]
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                int pos = node.getStartPosition();
                if (pos < start || pos >= end) return true;
                IBinding b = node.resolveBinding();
                if (b instanceof IVariableBinding vb && paramMap.containsKey(vb.getKey())) {
                    reps.add(new Object[]{pos, pos + node.getLength(), paramMap.get(vb.getKey())});
                }
                return true;
            }
        });

        reps.sort((a, b) -> (int) b[0] - (int) a[0]);

        StringBuilder sb = new StringBuilder(source.substring(start, end));
        for (Object[] rep : reps) {
            int localStart = (int) rep[0] - start;
            int localEnd   = (int) rep[1] - start;
            sb.replace(localStart, localEnd, (String) rep[2]);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // AST navigation
    // -------------------------------------------------------------------------

    private static MethodInvocation findMethodInvocation(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) throw new IllegalArgumentException("No AST node at offset " + offset);
        // Walk up to MethodInvocation
        while (node != null && !(node instanceof MethodInvocation)) node = node.getParent();
        if (!(node instanceof MethodInvocation mi)) {
            throw new IllegalArgumentException(
                    "No method call found at offset " + offset + ".");
        }
        return mi;
    }

    private static MethodDeclaration findMethodDeclaration(
            CompilationUnit cu, IMethodBinding binding) {
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

    private static MethodDeclaration findEnclosingMethod(ASTNode node) {
        while (node != null && !(node instanceof MethodDeclaration)) node = node.getParent();
        return (MethodDeclaration) node;
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    private static CompilationUnit parse(String source, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        return (CompilationUnit) parser.createAST(null);
    }
}
