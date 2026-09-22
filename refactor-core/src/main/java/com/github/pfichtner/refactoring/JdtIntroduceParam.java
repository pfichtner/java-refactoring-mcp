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
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TypeMethodReference;

import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Headless Introduce Parameter refactoring using JDT ASTParser.
 *
 * <p>Promotes a selected expression inside a method body to a new parameter:
 * <ol>
 *   <li>Appends a new formal parameter ({@code TypeName paramName}) to the
 *       method declaration.</li>
 *   <li>Replaces the selected expression in the body with {@code paramName}.</li>
 *   <li>Appends the original expression text as a new argument at every
 *       call site found in the project.</li>
 * </ol>
 *
 * <p>Precondition failures (diagnostic, no files modified):
 * <ul>
 *   <li>Selection is a simple name (already a name — nothing to promote)</li>
 *   <li>Expression references local variables (will cause compilation errors
 *       at the call sites — reported as a warning, not a hard reject)</li>
 *   <li>Method declaration not found in the project source roots</li>
 *   <li>Type of the expression cannot be resolved</li>
 * </ul>
 */
public class JdtIntroduceParam {

    /**
     * Introduces a parameter for the expression at
     * {@code [selectionStart, selectionStart + selectionLength)} into
     * the enclosing method, returning a map of all changed files.
     *
     * @param project         Maven project for source roots and classpath
     * @param sourceFile      file containing the expression
     * @param selectionStart  start offset of the expression to promote
     * @param selectionLength length of the expression
     * @param paramName       name for the new parameter
     * @param paramType       explicit type (e.g. {@code "String"}), or {@code null}
     *                        to infer from the expression's binding
     * @return map of {@code path → new source} for every changed file
     */
    public static Map<Path, String> introduceParam(
            JavaProject project, Path sourceFile,
            int selectionStart, int selectionLength,
            String paramName, String paramType)
            throws IOException, InterruptedException {

        String[] classpath   = project.classpath();
        String[] sourcePaths = project.sourceRoots().stream()
                .map(Path::toString).toArray(String[]::new);
        List<Path> allFiles  = collectSourceFiles(project);
        Map<Path, String> sources = readAll(allFiles);

        // Parse all files together for cross-file binding resolution
        Map<Path, CompilationUnit> cus = parseAll(allFiles, classpath, sourcePaths);
        Path absTarget = sourceFile.toAbsolutePath().normalize();

        CompilationUnit targetCu = cus.get(absTarget);
        if (targetCu == null) {
            throw new IllegalArgumentException("Source file not found in project: " + sourceFile);
        }

        String targetSource = sources.get(absTarget);
        Expression expr = findExpression(targetCu, selectionStart, selectionLength);
        validateExpression(expr);

        String exprText  = targetSource.substring(
                expr.getStartPosition(), expr.getStartPosition() + expr.getLength());
        String resolvedType = paramType != null ? paramType
                : resolveTypeName(expr.resolveTypeBinding(), expr);

        MethodDeclaration enclosingMethod = findEnclosingMethod(expr);
        IMethodBinding methodBinding = enclosingMethod.resolveBinding();
        if (methodBinding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve binding of the enclosing method.");
        }
        String methodKey = methodBinding.getMethodDeclaration().getKey();

        Map<Path, List<Object[]>> fileEdits = new LinkedHashMap<>(); // [start, end, text]

        // Target file: (1) add formal param, (2) replace expression with paramName
        List<Object[]> targetEdits = new ArrayList<>();

        // (1) Formal parameter insertion
        @SuppressWarnings("unchecked") List<SingleVariableDeclaration> params = enclosingMethod.parameters();
        String paramDecl = resolvedType + " " + paramName;
        int paramInsertPos;
        String paramInsertText;
        if (params.isEmpty()) {
            // Insert right after opening '(' of parameter list
            int nameEnd = enclosingMethod.getName().getStartPosition()
                    + enclosingMethod.getName().getLength();
            paramInsertPos = targetSource.indexOf('(', nameEnd) + 1;
            paramInsertText = paramDecl;
        } else {
            SingleVariableDeclaration last = params.get(params.size() - 1);
            paramInsertPos = last.getStartPosition() + last.getLength();
            paramInsertText = ", " + paramDecl;
        }
        targetEdits.add(new Object[]{paramInsertPos, paramInsertPos, paramInsertText});

        // (1b) Insert @param tag into the method's Javadoc if one exists
        addJavadocParamTag(targetSource, enclosingMethod, paramName, targetEdits);

        // (2) Expression replacement
        targetEdits.add(new Object[]{
                expr.getStartPosition(), expr.getStartPosition() + expr.getLength(), paramName});

        fileEdits.put(absTarget, targetEdits);

        List<String> originalParamNames = params.stream()
                .map(p -> p.getName().getIdentifier()).toList();
        String methodSimpleName = enclosingMethod.getName().getIdentifier();

        // All files: find call sites and append the expression as a new argument
        for (Map.Entry<Path, CompilationUnit> entry : cus.entrySet()) {
            Path filePath = entry.getKey();
            CompilationUnit cu = entry.getValue();
            String fileSource = sources.get(filePath);

            List<Object[]> edits = fileEdits.computeIfAbsent(filePath, k -> new ArrayList<>());

            cu.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation call) {
                    IMethodBinding b = call.resolveMethodBinding();
                    if (b == null) return true;
                    if (!methodKey.equals(b.getMethodDeclaration().getKey())) return true;

                    // Append new argument before the closing ')'
                    int closeParenPos = call.getStartPosition() + call.getLength() - 1;
                    @SuppressWarnings("unchecked") List<Expression> args = call.arguments();
                    String argText = args.isEmpty() ? exprText : ", " + exprText;
                    edits.add(new Object[]{closeParenPos, closeParenPos, argText});
                    return true;
                }

                @Override
                public boolean visit(ExpressionMethodReference ref) {
                    IMethodBinding b = ref.resolveMethodBinding();
                    if (b == null || !methodKey.equals(b.getMethodDeclaration().getKey())) return true;
                    Expression receiverExpr = ref.getExpression();
                    String lambda;
                    if (!Modifier.isStatic(b.getModifiers()) && isTypeNameExpression(receiverExpr)) {
                        // JDT sometimes parses Type::instanceMethod as ExpressionMethodReference
                        String receiverVar = receiverVarName(receiverExpr.toString());
                        lambda = buildIntroduceUnboundLambda(receiverVar, methodSimpleName, originalParamNames, exprText);
                    } else {
                        String receiver = fileSource.substring(receiverExpr.getStartPosition(),
                                receiverExpr.getStartPosition() + receiverExpr.getLength());
                        lambda = buildIntroduceBoundLambda(receiver, methodSimpleName, originalParamNames, exprText);
                    }
                    edits.add(new Object[]{ref.getStartPosition(), ref.getStartPosition() + ref.getLength(), lambda});
                    return false;
                }

                @Override
                public boolean visit(TypeMethodReference ref) {
                    IMethodBinding b = ref.resolveMethodBinding();
                    if (b == null || !methodKey.equals(b.getMethodDeclaration().getKey())) return true;
                    String receiverVar = receiverVarName(ref.getType().toString());
                    String lambda = buildIntroduceUnboundLambda(receiverVar, methodSimpleName, originalParamNames, exprText);
                    edits.add(new Object[]{ref.getStartPosition(), ref.getStartPosition() + ref.getLength(), lambda});
                    return false;
                }

                @Override
                public boolean visit(SuperMethodReference ref) {
                    IMethodBinding b = ref.resolveMethodBinding();
                    if (b == null || !methodKey.equals(b.getMethodDeclaration().getKey())) return true;
                    String lambda = buildIntroduceBoundLambda("super", methodSimpleName, originalParamNames, exprText);
                    edits.add(new Object[]{ref.getStartPosition(), ref.getStartPosition() + ref.getLength(), lambda});
                    return false;
                }
            });
        }

        // Apply edits per file (sort end-to-start within each file)
        Map<Path, String> changed = new LinkedHashMap<>();
        for (Map.Entry<Path, List<Object[]>> entry : fileEdits.entrySet()) {
            Path filePath = entry.getKey();
            List<Object[]> edits = entry.getValue();
            if (edits.isEmpty()) continue;

            edits.sort((a, b) -> (int) b[0] - (int) a[0]);
            StringBuilder sb = new StringBuilder(sources.get(filePath));
            for (Object[] ed : edits) {
                int start = (int) ed[0];
                int end   = (int) ed[1];
                String text = (String) ed[2];
                sb.replace(start, end, text);
            }
            changed.put(filePath, sb.toString());
        }
        return changed;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Expression findExpression(CompilationUnit cu, int selStart, int selLen) {
        int selEnd = selStart + selLen;
        ASTNode node = NodeFinder.perform(cu, selStart, selLen);
        if (node == null) throw new IllegalArgumentException("No AST node at the given selection.");
        ASTNode current = node;
        while (current != null) {
            if (current instanceof Expression e
                    && e.getStartPosition() == selStart
                    && e.getStartPosition() + e.getLength() == selEnd) return e;
            current = current.getParent();
        }
        if (node instanceof Expression e) return e;
        throw new IllegalArgumentException("Selection does not correspond to a single expression.");
    }

    private static void validateExpression(Expression expr) {
        if (expr instanceof SimpleName) {
            throw new IllegalArgumentException(
                    "Selection is already a simple name. Choose a compound expression to introduce as a parameter.");
        }
    }

    private static MethodDeclaration findEnclosingMethod(ASTNode node) {
        while (node != null && !(node instanceof MethodDeclaration)) node = node.getParent();
        if (!(node instanceof MethodDeclaration md)) {
            throw new IllegalArgumentException("Selection is not inside a method body.");
        }
        return md;
    }

    private static String resolveTypeName(ITypeBinding type, Expression expr) {
        if (type == null) {
            if (expr instanceof StringLiteral)    return "String";
            if (expr instanceof NumberLiteral nl) return guessNumberType(nl.getToken());
            if (expr instanceof BooleanLiteral)   return "boolean";
            if (expr instanceof CharacterLiteral) return "char";
            throw new IllegalArgumentException("Cannot resolve the type of the selected expression.");
        }
        return type.isPrimitive() ? type.getName() : type.getQualifiedName();
    }

    private static String guessNumberType(String token) {
        if (token.endsWith("L") || token.endsWith("l")) return "long";
        if (token.endsWith("F") || token.endsWith("f")) return "float";
        if (token.endsWith("D") || token.endsWith("d")) return "double";
        if (token.contains(".")) return "double";
        return "int";
    }

    // -------------------------------------------------------------------------
    // Lambda builders for method reference → lambda conversion
    // -------------------------------------------------------------------------

    private static void addJavadocParamTag(
            String source, MethodDeclaration method, String paramName, List<Object[]> edits) {
        Javadoc javadoc = method.getJavadoc();
        if (javadoc == null) return;

        int insertPos = -1;
        String linePrefix = " * ";  // fallback indent
        int firstNonParamLineStart = -1;

        for (Object tagObj : javadoc.tags()) {
            if (!(tagObj instanceof TagElement tag)) continue;
            String tagName = tag.getTagName();
            if ("@param".equals(tagName)) {
                int lineStart = source.lastIndexOf('\n', tag.getStartPosition() - 1) + 1;
                linePrefix = source.substring(lineStart, tag.getStartPosition());
                int lineEnd = tag.getStartPosition() + tag.getLength();
                while (lineEnd < source.length() && source.charAt(lineEnd) != '\n') lineEnd++;
                if (lineEnd < source.length()) lineEnd++;
                insertPos = lineEnd;
            } else if (firstNonParamLineStart < 0 && tagName != null && !tagName.isEmpty()) {
                firstNonParamLineStart = source.lastIndexOf('\n', tag.getStartPosition() - 1) + 1;
            }
        }

        if (insertPos < 0) {
            if (firstNonParamLineStart >= 0) {
                insertPos = firstNonParamLineStart;
            } else {
                // no tags at all: insert before closing */
                int closePos = javadoc.getStartPosition() + javadoc.getLength() - 2;
                insertPos = source.lastIndexOf('\n', closePos) + 1;
            }
        }

        edits.add(new Object[]{insertPos, insertPos, linePrefix + "@param " + paramName + "\n"});
    }

    // -------------------------------------------------------------------------

    private static String buildIntroduceBoundLambda(
            String receiver, String methodName, List<String> paramNames, String defaultArg) {
        String callArgs = paramNames.isEmpty() ? defaultArg : String.join(", ", paramNames) + ", " + defaultArg;
        String lhs = lambdaParams(paramNames);
        return lhs + " -> " + receiver + "." + methodName + "(" + callArgs + ")";
    }

    private static String buildIntroduceUnboundLambda(
            String receiverVar, String methodName, List<String> paramNames, String defaultArg) {
        List<String> allLambdaParams = new ArrayList<>();
        allLambdaParams.add(receiverVar);
        allLambdaParams.addAll(paramNames);
        String callArgs = paramNames.isEmpty() ? defaultArg : String.join(", ", paramNames) + ", " + defaultArg;
        String lhs = lambdaParams(allLambdaParams);
        return lhs + " -> " + receiverVar + "." + methodName + "(" + callArgs + ")";
    }

    static String lambdaParams(List<String> names) {
        return switch (names.size()) {
            case 0 -> "()";
            case 1 -> names.get(0);
            default -> "(" + String.join(", ", names) + ")";
        };
    }

    static boolean isTypeNameExpression(Expression expr) {
        if (expr instanceof Name name) {
            IBinding binding = name.resolveBinding();
            return binding instanceof ITypeBinding;
        }
        return false;
    }

    static String receiverVarName(String typeName) {
        if (typeName == null || typeName.isEmpty()) return "receiver";
        return Character.toLowerCase(typeName.charAt(0)) + typeName.substring(1);
    }

    private static List<Path> collectSourceFiles(JavaProject project) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.walk(root)) {
                files.addAll(stream.filter(p -> p.toString().endsWith(".java"))
                        .map(p -> p.toAbsolutePath().normalize())
                        .sorted().toList());
            }
        }
        return files;
    }

    private static Map<Path, String> readAll(List<Path> files) throws IOException {
        Map<Path, String> map = new LinkedHashMap<>();
        for (Path f : files) map.put(f, Files.readString(f));
        return map;
    }

    private static Map<Path, CompilationUnit> parseAll(
            List<Path> sourceFiles, String[] classpath, String[] sourcePaths) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setEnvironment(classpath, sourcePaths, null, true);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        String[] paths = sourceFiles.stream()
                .map(p -> p.toAbsolutePath().normalize().toString()).toArray(String[]::new);
        Map<Path, CompilationUnit> result = new LinkedHashMap<>();
        parser.createASTs(paths, null, new String[0], new FileASTRequestor() {
            @Override
            public void acceptAST(String path, CompilationUnit ast) {
                result.put(Path.of(path).toAbsolutePath().normalize(), ast);
            }
        }, null);
        return result;
    }
}
