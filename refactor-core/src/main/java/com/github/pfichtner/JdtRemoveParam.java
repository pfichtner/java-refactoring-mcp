package com.github.pfichtner;

import com.github.pfichtner.project.JavaProject;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Headless Remove Parameter refactoring using JDT ASTParser.
 *
 * <p>Removes an unused formal parameter from a method and the corresponding
 * argument from every call site in the project.
 *
 * <p>Precondition failures (diagnostic, no files modified):
 * <ul>
 *   <li>The parameter is referenced in the method body — remove usages first</li>
 *   <li>No parameter found at the given offset</li>
 *   <li>Method declaration binding cannot be resolved</li>
 * </ul>
 */
public class JdtRemoveParam {

    /**
     * Removes the parameter at {@code offset} from its method and updates all
     * call sites in the project.
     *
     * @param project    Maven project providing source roots and classpath
     * @param sourceFile file containing the parameter declaration
     * @param offset     character offset of any character within the parameter name
     * @return map of {@code path → new source} for every changed file
     */
    public static Map<Path, String> removeParam(
            JavaProject project, Path sourceFile, int offset)
            throws IOException, InterruptedException {

        String[] classpath   = project.classpath();
        String[] sourcePaths = project.sourceRoots().stream()
                .map(Path::toString).toArray(String[]::new);
        List<Path> allFiles  = collectSourceFiles(project);
        Map<Path, String> sources = readAll(allFiles);

        Map<Path, CompilationUnit> cus = parseAll(allFiles, classpath, sourcePaths);
        Path absTarget = sourceFile.toAbsolutePath().normalize();
        CompilationUnit targetCu = cus.get(absTarget);
        if (targetCu == null) {
            throw new IllegalArgumentException("Source file not found in project: " + sourceFile);
        }

        String targetSource = sources.get(absTarget);

        // Find the parameter
        SingleVariableDeclaration param = findParameter(targetCu, offset);
        IVariableBinding paramBinding   = param.resolveBinding();
        if (paramBinding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve binding of the parameter at offset " + offset + ".");
        }

        // Find enclosing method
        MethodDeclaration decl = (MethodDeclaration) param.getParent();
        @SuppressWarnings("unchecked") List<SingleVariableDeclaration> params = decl.parameters();
        int paramIndex = params.indexOf(param);

        // Validate: parameter must not be used in the method body
        Block body = decl.getBody();
        if (body != null) {
            String paramKey = paramBinding.getKey();
            boolean[] used = {false};
            body.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName node) {
                    IBinding b = node.resolveBinding();
                    if (b != null && paramKey.equals(b.getKey())) used[0] = true;
                    return !used[0];
                }
            });
            if (used[0]) {
                throw new IllegalArgumentException(
                        "Cannot remove parameter '" + paramBinding.getName()
                        + "': it is referenced in the method body. Remove all usages first.");
            }
        }

        IMethodBinding methodBinding = decl.resolveBinding();
        if (methodBinding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve binding of the enclosing method.");
        }
        String methodKey = methodBinding.getMethodDeclaration().getKey();

        List<String> originalParamNames = params.stream()
                .map(p -> p.getName().getIdentifier()).toList();
        String methodSimpleName = decl.getName().getIdentifier();

        // -------------------------------------------------------------------------
        // Build edits: Object[] = [start, end, replacementText], "" means delete
        // -------------------------------------------------------------------------
        Map<Path, List<Object[]>> fileEdits = new LinkedHashMap<>();

        // Target file: remove the parameter from the declaration
        List<Object[]> targetEdits = fileEdits.computeIfAbsent(absTarget, k -> new ArrayList<>());
        targetEdits.add(toDelete(paramRemoveRange(targetSource, params, paramIndex)));

        // Remove orphaned @param tag from the method's Javadoc
        Javadoc javadoc = decl.getJavadoc();
        if (javadoc != null) {
            String paramName = paramBinding.getName();
            for (Object tagObj : javadoc.tags()) {
                if (tagObj instanceof TagElement tag
                        && "@param".equals(tag.getTagName())
                        && !tag.fragments().isEmpty()
                        && tag.fragments().get(0) instanceof SimpleName sn
                        && sn.getIdentifier().equals(paramName)) {
                    targetEdits.add(toDelete(tagLineRange(targetSource, tag)));
                }
            }
        }

        // All files: remove the argument at paramIndex from each matching call site
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
                    @SuppressWarnings("unchecked") List<Expression> args = call.arguments();
                    if (paramIndex >= args.size()) return true;
                    edits.add(toDelete(argRemoveRange(fileSource, args, paramIndex)));
                    return true;
                }

                @Override
                public boolean visit(ExpressionMethodReference ref) {
                    IMethodBinding b = ref.resolveMethodBinding();
                    if (b == null || !methodKey.equals(b.getMethodDeclaration().getKey())) return true;
                    Expression receiverExpr = ref.getExpression();
                    String lambda;
                    if (!Modifier.isStatic(b.getModifiers()) && JdtIntroduceParam.isTypeNameExpression(receiverExpr)) {
                        String receiverVar = JdtIntroduceParam.receiverVarName(receiverExpr.toString());
                        lambda = buildRemoveUnboundLambda(receiverVar, methodSimpleName, originalParamNames, paramIndex);
                    } else {
                        String receiver = fileSource.substring(receiverExpr.getStartPosition(),
                                receiverExpr.getStartPosition() + receiverExpr.getLength());
                        lambda = buildRemoveBoundLambda(receiver, methodSimpleName, originalParamNames, paramIndex);
                    }
                    edits.add(new Object[]{ref.getStartPosition(), ref.getStartPosition() + ref.getLength(), lambda});
                    return false;
                }

                @Override
                public boolean visit(TypeMethodReference ref) {
                    IMethodBinding b = ref.resolveMethodBinding();
                    if (b == null || !methodKey.equals(b.getMethodDeclaration().getKey())) return true;
                    String receiverVar = JdtIntroduceParam.receiverVarName(ref.getType().toString());
                    String lambda = buildRemoveUnboundLambda(receiverVar, methodSimpleName, originalParamNames, paramIndex);
                    edits.add(new Object[]{ref.getStartPosition(), ref.getStartPosition() + ref.getLength(), lambda});
                    return false;
                }

                @Override
                public boolean visit(SuperMethodReference ref) {
                    IMethodBinding b = ref.resolveMethodBinding();
                    if (b == null || !methodKey.equals(b.getMethodDeclaration().getKey())) return true;
                    String lambda = buildRemoveBoundLambda("super", methodSimpleName, originalParamNames, paramIndex);
                    edits.add(new Object[]{ref.getStartPosition(), ref.getStartPosition() + ref.getLength(), lambda});
                    return false;
                }
            });
        }

        // Apply edits per file (end-to-start within each file)
        Map<Path, String> changed = new LinkedHashMap<>();
        for (Map.Entry<Path, List<Object[]>> entry : fileEdits.entrySet()) {
            Path filePath = entry.getKey();
            List<Object[]> edits = entry.getValue();
            if (edits.isEmpty()) continue;

            edits.sort((a, b) -> (int) b[0] - (int) a[0]);
            StringBuilder sb = new StringBuilder(sources.get(filePath));
            for (Object[] ed : edits) {
                sb.replace((int) ed[0], (int) ed[1], (String) ed[2]);
            }
            changed.put(filePath, sb.toString());
        }
        return changed;
    }

    // -------------------------------------------------------------------------
    // Range calculation helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the source range {@code [start, end)} to delete for removing the
     * parameter at {@code index} from the declaration's parameter list.
     * Includes the adjacent comma and whitespace so the result is syntactically valid.
     */
    private static int[] paramRemoveRange(
            String source, List<SingleVariableDeclaration> params, int index) {
        SingleVariableDeclaration p = params.get(index);
        int pStart = p.getStartPosition();
        int pEnd   = pStart + p.getLength();

        if (index > 0) {
            // Remove ", Type name" — delete from comma after previous param to end of this param
            SingleVariableDeclaration prev = params.get(index - 1);
            int prevEnd   = prev.getStartPosition() + prev.getLength();
            int commaPos  = source.indexOf(',', prevEnd);
            return new int[]{commaPos, pEnd};
        } else if (params.size() > 1) {
            // Remove "Type name, " — delete from start of this param to start of next param
            SingleVariableDeclaration next = params.get(1);
            return new int[]{pStart, next.getStartPosition()};
        } else {
            return new int[]{pStart, pEnd};
        }
    }

    /**
     * Returns the source range {@code [start, end)} to delete for removing the
     * argument at {@code index} from a call's argument list.
     */
    private static int[] argRemoveRange(
            String source, List<Expression> args, int index) {
        Expression arg  = args.get(index);
        int argStart    = arg.getStartPosition();
        int argEnd      = argStart + arg.getLength();

        if (index > 0) {
            Expression prev = args.get(index - 1);
            int prevEnd  = prev.getStartPosition() + prev.getLength();
            int commaPos = source.indexOf(',', prevEnd);
            return new int[]{commaPos, argEnd};
        } else if (args.size() > 1) {
            Expression next = args.get(1);
            return new int[]{argStart, next.getStartPosition()};
        } else {
            return new int[]{argStart, argEnd};
        }
    }

    private static Object[] toDelete(int[] range) {
        return new Object[]{range[0], range[1], ""};
    }

    private static int[] tagLineRange(String source, TagElement tag) {
        int tagStart = tag.getStartPosition();
        int tagEnd   = tagStart + tag.getLength();
        // extend backward to include the " * " prefix (from the preceding newline)
        int lineStart = source.lastIndexOf('\n', tagStart - 1) + 1;
        // extend forward to include the trailing newline
        int lineEnd = tagEnd;
        while (lineEnd < source.length() && source.charAt(lineEnd) != '\n') lineEnd++;
        if (lineEnd < source.length()) lineEnd++; // include the '\n' itself
        return new int[]{lineStart, lineEnd};
    }

    // -------------------------------------------------------------------------
    // Lambda builders for method reference → lambda conversion
    // -------------------------------------------------------------------------

    private static String buildRemoveBoundLambda(
            String receiver, String methodName, List<String> paramNames, int removeIndex) {
        List<String> callArgs = new ArrayList<>(paramNames);
        if (removeIndex < callArgs.size()) callArgs.remove(removeIndex);
        String lhs = JdtIntroduceParam.lambdaParams(paramNames);
        return lhs + " -> " + receiver + "." + methodName + "(" + String.join(", ", callArgs) + ")";
    }

    private static String buildRemoveUnboundLambda(
            String receiverVar, String methodName, List<String> paramNames, int removeIndex) {
        List<String> allLambdaParams = new ArrayList<>();
        allLambdaParams.add(receiverVar);
        allLambdaParams.addAll(paramNames);
        List<String> callArgs = new ArrayList<>(paramNames);
        if (removeIndex < callArgs.size()) callArgs.remove(removeIndex);
        String lhs = JdtIntroduceParam.lambdaParams(allLambdaParams);
        return lhs + " -> " + receiverVar + "." + methodName + "(" + String.join(", ", callArgs) + ")";
    }

    // -------------------------------------------------------------------------
    // AST navigation
    // -------------------------------------------------------------------------

    private static SingleVariableDeclaration findParameter(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) throw new IllegalArgumentException("No AST node at offset " + offset);
        // Walk up to SingleVariableDeclaration whose parent is MethodDeclaration
        while (node != null) {
            if (node instanceof SingleVariableDeclaration svd
                    && svd.getParent() instanceof MethodDeclaration) {
                return svd;
            }
            node = node.getParent();
        }
        throw new IllegalArgumentException(
                "No method parameter found at offset " + offset
                + ". Place the cursor within a formal parameter declaration.");
    }

    // -------------------------------------------------------------------------
    // Infrastructure (shared with JdtIntroduceParam)
    // -------------------------------------------------------------------------

    private static List<Path> collectSourceFiles(JavaProject project) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.walk(root)) {
                stream.filter(p -> p.toString().endsWith(".java"))
                        .map(p -> p.toAbsolutePath().normalize())
                        .sorted().forEach(files::add);
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
