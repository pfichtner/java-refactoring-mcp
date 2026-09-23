package com.github.pfichtner.refactoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Headless Inline Method refactoring using JDT ASTParser.
 *
 * <p>Replaces a method call with the body of the called method, substituting
 * formal parameters with actual argument expressions.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Single-file</b>: {@link #inlineMethod(String, String, int)} — call site
 *       and declaration must be in the same source string (snippets).</li>
 *   <li><b>Multi-file</b>: {@link #inlineMethod(JavaProject, Path, int, boolean)} —
 *       inlines at <em>all</em> call sites found in the project; optionally removes
 *       the method declaration.</li>
 * </ul>
 *
 * <p>Supported call contexts:
 * <ul>
 *   <li>Void method called as a statement (zero or more parameters)</li>
 *   <li>Value-returning method with a single {@code return} statement, used
 *       as an expression (in a declaration, return, or assignment)</li>
 * </ul>
 *
 * <p>Precondition failures (diagnostic, no files modified):
 * <ul>
 *   <li>Method declaration not found (single-file mode: not in same file;
 *       multi-file mode: not in project source roots)</li>
 *   <li>Method is abstract or native (no body)</li>
 *   <li>Void method body contains a {@code return} statement</li>
 *   <li>Value method has more than one statement in its body</li>
 *   <li>Recursive call</li>
 * </ul>
 */
public class JdtInlineMethod {

    // =========================================================================
    // Public API — single file (backward-compatible)
    // =========================================================================

    /**
     * Inlines the single method call at {@code offset} in {@code source}.
     * Declaration must be in the same source text.
     */
    public static String inlineMethod(String source, String unitName, int offset) {
        return inlineMethod(source, unitName, offset, false, false);
    }

    /**
     * Inlines the method identified by the call at {@code offset} in {@code source}.
     *
     * @param allOccurrences    if {@code true}, every call site in the file is inlined;
     *                          if {@code false}, only the single call at {@code offset} is inlined
     * @param removeDeclaration if {@code true}, the method declaration is also deleted
     *                          (requires {@code allOccurrences = true})
     */
    public static String inlineMethod(String source, String unitName, int offset,
                                      boolean allOccurrences, boolean removeDeclaration) {
        if (removeDeclaration && !allOccurrences) {
            throw new IllegalArgumentException(
                    "removeDeclaration requires allOccurrences=true — "
                    + "cannot remove declaration when only one call site is inlined.");
        }

        CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(source, unitName);

        MethodInvocation call = findMethodInvocation(cu, offset);
        IMethodBinding binding = call.resolveMethodBinding();
        if (binding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve method call at offset " + offset + ".");
        }
        String methodKey = binding.getMethodDeclaration().getKey();
        MethodDeclaration decl = findMethodDeclarationInCu(cu, methodKey);
        if (decl == null) {
            throw new IllegalArgumentException(
                    "Method '" + binding.getName()
                    + "' is not declared in this file. Use the project-based overload for cross-file inline.");
        }
        validateDeclaration(decl, binding.getName(), call, cu);

        @SuppressWarnings("unchecked") List<Statement> stmts = decl.getBody().statements();

        if (!allOccurrences) {
            Map<String, String> paramMap = buildParamMap(decl, call, source);
            ASTNode callParent = call.getParent();
            if (callParent instanceof ExpressionStatement callStmt) {
                Edit e = voidCallEdit(callStmt, source, source, cu, decl, stmts, paramMap);
                return e.apply(source);
            } else {
                Edit e = valueCallEdit(call, source, source, cu, decl, stmts, paramMap);
                return e.apply(source);
            }
        }

        List<MethodInvocation> calls = findAllCallSites(cu, methodKey);
        List<Edit> edits = new ArrayList<>();
        for (MethodInvocation c : calls) {
            Map<String, String> paramMap = buildParamMap(decl, c, source);
            ASTNode parent = c.getParent();
            if (parent instanceof ExpressionStatement cs) {
                edits.add(voidCallEdit(cs, source, source, cu, decl, stmts, paramMap));
            } else {
                edits.add(valueCallEdit(c, source, source, cu, decl, stmts, paramMap));
            }
        }

        if (removeDeclaration) {
            int mStart = decl.getStartPosition();
            int mEnd   = mStart + decl.getLength();
            int lineStart = mStart;
            while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
            int lineEnd = mEnd;
            while (lineEnd < source.length() && source.charAt(lineEnd) != '\n') lineEnd++;
            if (lineEnd < source.length()) lineEnd++;
            edits.add(new Edit(lineStart, lineEnd, ""));
        }

        edits.sort((a, b) -> b.start() - a.start());
        StringBuilder sb = new StringBuilder(source);
        for (Edit ed : edits) sb.replace(ed.start(), ed.end(), ed.replacement());
        return sb.toString();
    }

    // =========================================================================
    // Public API — multi-file
    // =========================================================================

    /**
     * Inlines the method referenced by the call at {@code offset} in
     * {@code sourceFile} at <em>every</em> call site found in the project.
     *
     * @param project           Maven project for source roots and classpath
     * @param sourceFile        file containing the call that identifies the method
     * @param offset            character offset of any character within the call name
     * @param removeDeclaration if {@code true}, also deletes the method declaration
     * @return map of {@code path → new source} for every changed file
     */
    public static Map<Path, String> inlineMethod(
            JavaProject project, Path sourceFile, int offset, boolean removeDeclaration)
            throws IOException, InterruptedException {

        String[] classpath   = project.classpath();
        String[] sourcePaths = project.sourceRoots().stream()
                .map(Path::toString).toArray(String[]::new);
        List<Path> allFiles  = JdtProjectSources.collectSourceFiles(project);
        Map<Path, String> sources = JdtProjectSources.readAll(allFiles);
        Map<Path, CompilationUnit> cus = JdtProjectSources.parseAll(allFiles, classpath, sourcePaths);

        Path absTarget = sourceFile.toAbsolutePath().normalize();
        CompilationUnit targetCu = cus.get(absTarget);
        if (targetCu == null) {
            throw new IllegalArgumentException("Source file not found in project: " + sourceFile);
        }

        // Identify the method to inline
        MethodInvocation refCall = findMethodInvocation(targetCu, offset);
        IMethodBinding binding   = refCall.resolveMethodBinding();
        if (binding == null) {
            throw new IllegalArgumentException("Cannot resolve method call at offset " + offset + ".");
        }
        String methodKey = binding.getMethodDeclaration().getKey();

        // Find declaration in the project
        Path declFile = null;
        MethodDeclaration decl = null;
        for (Map.Entry<Path, CompilationUnit> e : cus.entrySet()) {
            MethodDeclaration d = findMethodDeclarationInCu(e.getValue(), methodKey);
            if (d != null) { declFile = e.getKey(); decl = d; break; }
        }
        if (decl == null) {
            throw new IllegalArgumentException(
                    "Method '" + binding.getName() + "' declaration not found in project source roots.");
        }
        validateDeclaration(decl, binding.getName(), refCall, targetCu);

        @SuppressWarnings("unchecked") List<Statement> stmts = decl.getBody().statements();
        String declSource = sources.get(declFile);
        CompilationUnit declCu = cus.get(declFile);

        // Collect edits per file: List<[start, end, replacement]>
        Map<Path, List<Edit>> fileEdits = new LinkedHashMap<>();

        for (Map.Entry<Path, CompilationUnit> entry : cus.entrySet()) {
            Path filePath = entry.getKey();
            CompilationUnit fileCu = entry.getValue();
            String fileSource = sources.get(filePath);

            List<MethodInvocation> calls = findAllCallSites(fileCu, methodKey);
            if (calls.isEmpty()) continue;

            List<Edit> edits = new ArrayList<>();
            for (MethodInvocation c : calls) {
                Map<String, String> paramMap = buildParamMap(decl, c, fileSource);
                ASTNode parent = c.getParent();
                if (parent instanceof ExpressionStatement cs) {
                    edits.add(voidCallEdit(cs, fileSource, declSource, declCu, decl, stmts, paramMap));
                } else {
                    edits.add(valueCallEdit(c, fileSource, declSource, declCu, decl, stmts, paramMap));
                }
            }
            fileEdits.computeIfAbsent(filePath, k -> new ArrayList<>()).addAll(edits);
        }

        // Remove declaration if requested
        if (removeDeclaration && decl != null) {
            int mStart = decl.getStartPosition();
            int mEnd   = mStart + decl.getLength();
            int lineStart = mStart;
            while (lineStart > 0 && declSource.charAt(lineStart - 1) != '\n') lineStart--;
            int lineEnd = mEnd;
            while (lineEnd < declSource.length() && declSource.charAt(lineEnd) != '\n') lineEnd++;
            if (lineEnd < declSource.length()) lineEnd++;
            fileEdits.computeIfAbsent(declFile, k -> new ArrayList<>())
                    .add(new Edit(lineStart, lineEnd, ""));
        }

        // Apply edits (end-to-start within each file)
        Map<Path, String> changed = new LinkedHashMap<>();
        for (Map.Entry<Path, List<Edit>> entry : fileEdits.entrySet()) {
            Path filePath = entry.getKey();
            List<Edit> edits = entry.getValue();
            edits.sort((a, b) -> b.start() - a.start());
            StringBuilder sb = new StringBuilder(sources.get(filePath));
            for (Edit ed : edits) sb.replace(ed.start(), ed.end(), ed.replacement());
            changed.put(filePath, sb.toString());
        }
        return changed;
    }

    // =========================================================================
    // Edit computation
    // =========================================================================

    private record Edit(int start, int end, String replacement) {
        String apply(String source) {
            return source.substring(0, start) + replacement + source.substring(end);
        }
    }

    /** Returns an edit that replaces the call-statement line(s) with the inlined body. */
    private static Edit voidCallEdit(
            ExpressionStatement callStmt,
            String callSiteSource, String declSource, CompilationUnit declCu,
            MethodDeclaration decl, List<Statement> stmts,
            Map<String, String> paramMap) {

        for (Statement s : stmts) {
            if (s instanceof ReturnStatement) {
                throw new IllegalArgumentException(
                        "Cannot inline void method: body contains a return statement.");
            }
        }
        int stmtStart = callStmt.getStartPosition();
        int lineStart  = stmtStart;
        while (lineStart > 0 && callSiteSource.charAt(lineStart - 1) != '\n') lineStart--;
        String indent = callSiteSource.substring(lineStart, stmtStart);

        StringBuilder inlined = new StringBuilder();
        for (Statement s : stmts) {
            String sText = substituteInRange(
                    declSource, s.getStartPosition(), s.getStartPosition() + s.getLength(),
                    paramMap, declCu);
            inlined.append(indent).append(sText.strip()).append("\n");
        }
        if (!inlined.isEmpty() && inlined.charAt(inlined.length() - 1) == '\n') {
            inlined.deleteCharAt(inlined.length() - 1);
        }
        int callStmtEnd = callStmt.getStartPosition() + callStmt.getLength();
        return new Edit(lineStart, callStmtEnd, inlined.toString());
    }

    /** Returns an edit that replaces the call expression with the inlined return expression. */
    private static Edit valueCallEdit(
            MethodInvocation call,
            String callSiteSource, String declSource, CompilationUnit declCu,
            MethodDeclaration decl, List<Statement> stmts,
            Map<String, String> paramMap) {

        if (stmts.size() != 1 || !(stmts.get(0) instanceof ReturnStatement rs)) {
            throw new IllegalArgumentException(
                    "Can only inline a method used as an expression if the method "
                    + "has exactly one statement and it is a return statement.");
        }
        Expression returnExpr = rs.getExpression();
        if (returnExpr == null) throw new IllegalArgumentException("Return statement has no expression.");

        String exprText = substituteInRange(
                declSource, returnExpr.getStartPosition(),
                returnExpr.getStartPosition() + returnExpr.getLength(),
                paramMap, declCu);

        boolean needsParens = !(call.getParent() instanceof VariableDeclarationFragment
                || call.getParent() instanceof ReturnStatement
                || call.getParent() instanceof ExpressionStatement
                || call.getParent() instanceof Assignment);
        String replacement = needsParens ? "(" + exprText + ")" : exprText;
        return new Edit(call.getStartPosition(), call.getStartPosition() + call.getLength(), replacement);
    }

    // =========================================================================
    // Parameter substitution
    // =========================================================================

    private static Map<String, String> buildParamMap(
            MethodDeclaration decl, MethodInvocation call, String callSiteSource) {
        @SuppressWarnings("unchecked") List<SingleVariableDeclaration> params = decl.parameters();
        @SuppressWarnings("unchecked") List<Expression> args = call.arguments();
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < params.size(); i++) {
            IVariableBinding b = params.get(i).resolveBinding();
            if (b != null) {
                Expression arg = args.get(i);
                map.put(b.getKey(), callSiteSource.substring(
                        arg.getStartPosition(), arg.getStartPosition() + arg.getLength()));
            }
        }
        return map;
    }

    private static String substituteInRange(
            String source, int start, int end,
            Map<String, String> paramMap, CompilationUnit cu) {
        if (paramMap.isEmpty()) return source.substring(start, end);
        List<Object[]> reps = new ArrayList<>();
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
            sb.replace((int) rep[0] - start, (int) rep[1] - start, (String) rep[2]);
        }
        return sb.toString();
    }

    // =========================================================================
    // Validation
    // =========================================================================

    private static void validateDeclaration(
            MethodDeclaration decl, String name, MethodInvocation call, CompilationUnit callCu) {
        MethodDeclaration enclosing = findEnclosingMethod(call);
        if (enclosing != null) {
            IMethodBinding eb = enclosing.resolveBinding();
            IMethodBinding db = decl.resolveBinding();
            if (eb != null && db != null
                    && eb.getMethodDeclaration().getKey().equals(db.getMethodDeclaration().getKey())) {
                throw new IllegalArgumentException("Cannot inline a recursive call.");
            }
        }
        Block body = decl.getBody();
        if (body == null) {
            throw new IllegalArgumentException("Cannot inline '" + name + "': abstract or native.");
        }
        @SuppressWarnings("unchecked") List<Statement> stmts = body.statements();
        if (stmts.isEmpty()) {
            throw new IllegalArgumentException("Cannot inline '" + name + "': method body is empty.");
        }
    }

    // =========================================================================
    // AST navigation
    // =========================================================================

    private static MethodInvocation findMethodInvocation(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) throw new IllegalArgumentException("No AST node at offset " + offset);
        while (node != null && !(node instanceof MethodInvocation)) node = node.getParent();
        if (!(node instanceof MethodInvocation mi)) {
            throw new IllegalArgumentException("No method call found at offset " + offset + ".");
        }
        return mi;
    }

    private static MethodDeclaration findMethodDeclarationInCu(CompilationUnit cu, String key) {
        MethodDeclaration[] found = {null};
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                IMethodBinding b = node.resolveBinding();
                if (b != null && key.equals(b.getMethodDeclaration().getKey())) {
                    found[0] = node; return false;
                }
                return found[0] == null;
            }
        });
        return found[0];
    }

    private static List<MethodInvocation> findAllCallSites(CompilationUnit cu, String methodKey) {
        List<MethodInvocation> result = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                IMethodBinding b = node.resolveMethodBinding();
                if (b != null && methodKey.equals(b.getMethodDeclaration().getKey())) result.add(node);
                return true;
            }
        });
        return result;
    }

    private static MethodDeclaration findEnclosingMethod(ASTNode node) {
        while (node != null && !(node instanceof MethodDeclaration)) node = node.getParent();
        return (MethodDeclaration) node;
    }

    // =========================================================================
    // Multi-file infrastructure
}
