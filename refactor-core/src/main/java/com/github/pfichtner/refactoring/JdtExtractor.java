package com.github.pfichtner.refactoring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Headless Extract Method refactoring using JDT ASTParser + ASTRewrite.
 *
 * <p>Supported cases:
 * <ul>
 *   <li>No parameters, no return value (pure side-effects)</li>
 *   <li>Parameters: pre-declared variables that are read-only in the selection</li>
 *   <li>Return value: single variable declared in the selection and read after it</li>
 * </ul>
 *
 * <p>Precondition failures (returns a diagnostic, does not modify):
 * <ul>
 *   <li>Selection contains a {@code return} statement</li>
 *   <li>Selection writes to a variable declared outside the selection</li>
 *   <li>Multiple variables declared in the selection are read after it</li>
 * </ul>
 */
public class JdtExtractor {

    private record VariableInfo(String name, String typeName) {}

    /**
     * Extracts the statements in
     * {@code [selectionStart, selectionStart + selectionLength)} into a new
     * private method named {@code methodName} and returns the rewritten source.
     *
     * @param source          full source text
     * @param unitName        file name for binding resolution (e.g. {@code "Foo.java"})
     * @param selectionStart  character offset of the first character of the selection
     * @param selectionLength number of characters in the selection
     * @param methodName      name for the extracted method
     * @return rewritten source with the selection replaced by a call and the new
     *         method appended after the enclosing method
     * @throws IllegalArgumentException if the selection is not safe to extract
     */
    public static String extractMethod(
            SourceUnit unit,
            int selectionStart, int selectionLength,
            String methodName) {

        String source = unit.source();
        CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(source, unit.unitName());

        List<Statement> selected = findSelectedStatements(cu, selectionStart, selectionLength);
        if (selected.isEmpty()) {
            throw new IllegalArgumentException(
                    "Selection does not contain any complete statements.");
        }

        validateNoReturn(selected);

        MethodDeclaration enclosing = findEnclosingMethod(selected.get(0));

        Set<String> declaredKeys = collectDeclaredKeys(selected);
        validateNoExternalWrite(selected, declaredKeys);

        List<VariableInfo> params = findParams(selected, declaredKeys);
        List<VariableInfo> returnCandidates = findReturnCandidates(selected, declaredKeys, enclosing);

        if (returnCandidates.size() > 1) {
            String names = returnCandidates.stream()
                    .map(VariableInfo::name).collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Cannot extract: multiple variables would need to be returned ("
                    + names + "). Narrow the selection.");
        }

        Optional<VariableInfo> returnVar = returnCandidates.isEmpty()
                ? Optional.empty()
                : Optional.of(returnCandidates.get(0));

        String callText  = buildCall(methodName, params, returnVar);
        String newMethod = buildMethod(source, selected, methodName, params, returnVar, enclosing);

        return applyChanges(source, selected, callText, newMethod, enclosing);
    }

    // -------------------------------------------------------------------------
    // Statement selection
    // -------------------------------------------------------------------------

    private static List<Statement> findSelectedStatements(
            CompilationUnit cu, int selectionStart, int selectionLength) {
        int selectionEnd = selectionStart + selectionLength;

        ASTNode node = NodeFinder.perform(cu, selectionStart, selectionLength);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at the given selection.");
        }
        while (node != null && !(node instanceof Block)) {
            node = node.getParent();
        }
        if (!(node instanceof Block block)) {
            throw new IllegalArgumentException(
                    "Selection is not within a statement block.");
        }

        @SuppressWarnings("unchecked")
        List<Object> stmts = block.statements();
        return stmts.stream()
                .map(o -> (Statement) o)
                .filter(s -> s.getStartPosition() >= selectionStart
                        && s.getStartPosition() + s.getLength() <= selectionEnd)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private static void validateNoReturn(List<Statement> selected) {
        ASTVisitor visitor = new ASTVisitor() {
            @Override
            public boolean visit(ReturnStatement node) {
                throw new IllegalArgumentException(
                        "Selection contains a return statement. Cannot extract safely.");
            }
        };
        selected.forEach(s -> s.accept(visitor));
    }

    private static void validateNoExternalWrite(
            List<Statement> selected, Set<String> declaredKeys) {
        ASTVisitor visitor = new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                IBinding b = node.resolveBinding();
                if (b instanceof IVariableBinding vb
                        && !vb.isField()
                        && !vb.isEnumConstant()
                        && !declaredKeys.contains(vb.getKey())
                        && isWrite(node)) {
                    throw new IllegalArgumentException(
                            "Cannot extract: selection writes to variable '"
                            + vb.getName()
                            + "' that is declared outside the selection.");
                }
                return true;
            }
        };
        selected.forEach(s -> s.accept(visitor));
    }

    // -------------------------------------------------------------------------
    // Data-flow analysis
    // -------------------------------------------------------------------------

    /** Keys of variables whose declarations are inside the selection. */
    private static Set<String> collectDeclaredKeys(List<Statement> selected) {
        Set<String> keys = new LinkedHashSet<>();
        ASTVisitor v = new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                IVariableBinding b = node.resolveBinding();
                if (b != null) keys.add(b.getKey());
                return true;
            }
            @Override
            public boolean visit(SingleVariableDeclaration node) {
                IVariableBinding b = node.resolveBinding();
                if (b != null) keys.add(b.getKey());
                return true;
            }
        };
        selected.forEach(s -> s.accept(v));
        return keys;
    }

    /** Variables that appear (read) in the selection but are declared outside it. */
    private static List<VariableInfo> findParams(
            List<Statement> selected, Set<String> declaredKeys) {
        List<VariableInfo> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        ASTVisitor v = new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                IBinding b = node.resolveBinding();
                if (b instanceof IVariableBinding vb
                        && !vb.isField()
                        && !vb.isEnumConstant()
                        && !declaredKeys.contains(vb.getKey())
                        && !isWrite(node)
                        && seen.add(vb.getKey())) {
                    result.add(new VariableInfo(vb.getName(), typeName(vb.getType())));
                }
                return true;
            }
        };
        selected.forEach(s -> s.accept(v));
        return result;
    }

    /**
     * Variables declared inside the selection that are read in statements
     * following the selection within the same block.
     */
    private static List<VariableInfo> findReturnCandidates(
            List<Statement> selected, Set<String> declaredKeys,
            MethodDeclaration enclosing) {
        int lastEnd = selected.get(selected.size() - 1).getStartPosition()
                + selected.get(selected.size() - 1).getLength();

        Block body = enclosing.getBody();
        List<VariableInfo> candidates = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        ASTVisitor v = new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                IBinding b = node.resolveBinding();
                if (b instanceof IVariableBinding vb
                        && declaredKeys.contains(vb.getKey())
                        && seen.add(vb.getKey())) {
                    candidates.add(new VariableInfo(vb.getName(), typeName(vb.getType())));
                }
                return true;
            }
        };

        for (Object o : body.statements()) {
            Statement s = (Statement) o;
            if (s.getStartPosition() >= lastEnd) {
                s.accept(v);
            }
        }
        return candidates;
    }

    // -------------------------------------------------------------------------
    // Code generation
    // -------------------------------------------------------------------------

    private static String buildCall(
            String methodName, List<VariableInfo> params, Optional<VariableInfo> returnVar) {
        StringBuilder sb = new StringBuilder();
        returnVar.ifPresent(rv -> sb.append(rv.typeName()).append(" ").append(rv.name()).append(" = "));
        sb.append(methodName).append("(");
        sb.append(params.stream().map(VariableInfo::name).collect(Collectors.joining(", ")));
        sb.append(");");
        return sb.toString();
    }

    private static String buildMethod(
            String source, List<Statement> selected,
            String methodName, List<VariableInfo> params,
            Optional<VariableInfo> returnVar,
            MethodDeclaration enclosing) {

        // Detect static enclosing method
        boolean isStatic = enclosing.modifiers().stream()
                .anyMatch(m -> m instanceof Modifier mod && mod.isStatic());

        // Enclosing method indentation → determines extracted method indentation
        int mPos = enclosing.getStartPosition();
        int mLineStart = mPos;
        while (mLineStart > 0 && source.charAt(mLineStart - 1) != '\n') mLineStart--;
        String methodIndent = source.substring(mLineStart, mPos);
        String stmtIndent   = methodIndent + "    ";

        String returnType = returnVar.map(VariableInfo::typeName).orElse("void");
        String paramList  = params.stream()
                .map(p -> p.typeName() + " " + p.name())
                .collect(Collectors.joining(", "));

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n").append(methodIndent);
        if (isStatic) sb.append("static ");
        sb.append("private ").append(returnType).append(" ")
                .append(methodName).append("(").append(paramList).append(") {\n");

        for (Statement stmt : selected) {
            String text = source.substring(
                    stmt.getStartPosition(),
                    stmt.getStartPosition() + stmt.getLength()).strip();
            sb.append(stmtIndent).append(text).append("\n");
        }

        returnVar.ifPresent(variableInfo -> sb.append(stmtIndent).append("return ").append(variableInfo.name()).append(";\n"));
        sb.append(methodIndent).append("}");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Apply changes
    // -------------------------------------------------------------------------

    private static String applyChanges(
            String source, List<Statement> selected,
            String callText, String newMethodText,
            MethodDeclaration enclosing) {

        int selStart = selected.get(0).getStartPosition();
        int selEnd   = selected.get(selected.size() - 1).getStartPosition()
                + selected.get(selected.size() - 1).getLength();
        int methodEnd = enclosing.getStartPosition() + enclosing.getLength();

        // Find line start of first selected statement (to get its indentation)
        int lineStart = selStart;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
        String indent = source.substring(lineStart, selStart);

        // part1: everything before the line containing the first selected statement
        // part2: indent + call
        // part3: source between selection end and method end
        // part4: new method text
        // part5: source after method end
        return source.substring(0, lineStart)
                + indent + callText
                + source.substring(selEnd, methodEnd)
                + newMethodText
                + source.substring(methodEnd);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static MethodDeclaration findEnclosingMethod(ASTNode node) {
        while (node != null && !(node instanceof MethodDeclaration)) {
            node = node.getParent();
        }
        if (!(node instanceof MethodDeclaration md)) {
            throw new IllegalArgumentException(
                    "Selection is not inside a method.");
        }
        return md;
    }

    private static boolean isWrite(SimpleName name) {
        ASTNode parent = name.getParent();
        if (parent instanceof Assignment a && a.getLeftHandSide() == name) return true;
        if (parent instanceof PostfixExpression) return true;
        if (parent instanceof PrefixExpression pe) {
            PrefixExpression.Operator op = pe.getOperator();
            return op == PrefixExpression.Operator.INCREMENT
                    || op == PrefixExpression.Operator.DECREMENT;
        }
        return false;
    }

    private static String typeName(ITypeBinding type) {
        if (type == null) return "Object";
        return type.getName(); // simple name; works for primitives and non-generic classes
    }
}
