package com.github.pfichtner.refactoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;

import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Headless Change Method Signature refactoring using JDT ASTParser.
 *
 * <p>Supports two independent axes:
 * <ul>
 *   <li><b>Parameter reorder</b>: supply {@code paramOrder} — an int array where
 *       {@code paramOrder[i]} is the index of the original parameter that should
 *       appear at position {@code i} in the new signature.  All call sites in the
 *       project are updated to match.</li>
 *   <li><b>Return type change</b>: supply {@code newReturnType} (e.g. {@code "double"},
 *       {@code "List<String>"}). Only the declaration is modified; callers are not
 *       changed (type compatibility is the caller's responsibility).</li>
 * </ul>
 *
 * <p>At least one of the two must be provided.
 *
 * <p>Precondition failures (diagnostic, no files modified):
 * <ul>
 *   <li>No method declaration at offset</li>
 *   <li>Binding cannot be resolved (no classpath)</li>
 *   <li>paramOrder length does not match parameter count</li>
 *   <li>paramOrder is not a valid permutation</li>
 *   <li>Both newReturnType and paramOrder are null / identity</li>
 * </ul>
 */
public class JdtChangeMethodSignature {

    /**
     * Changes the signature of the method identified by {@code offset} in {@code sourceFile}.
     *
     * @param project       project providing source roots and classpath
     * @param sourceFile    file containing the method declaration
     * @param offset        character offset within the method name
     * @param newReturnType new return type source text, or {@code null} to leave unchanged
     * @param paramOrder    permutation array for parameters (0-based); {@code null} to leave unchanged
     * @return map of absolute path → new source for every changed file
     */
    public static Map<Path, String> changeSignature(
            JavaProject project, Path sourceFile, int offset,
            String newReturnType, int[] paramOrder)
            throws IOException, InterruptedException {

        String[] classpath = project.classpath();
        String[] sourcePaths = project.sourceRoots().stream()
                .map(Path::toString).toArray(String[]::new);
        List<Path> allFiles = collectSourceFiles(project);
        Map<Path, String> sources = readAll(allFiles);
        Map<Path, CompilationUnit> cus = parseAll(allFiles, classpath, sourcePaths);

        Path absTarget = sourceFile.toAbsolutePath().normalize();
        CompilationUnit targetCu = cus.get(absTarget);
        if (targetCu == null) {
            throw new IllegalArgumentException("Source file not found in project: " + sourceFile);
        }

        // Locate the method declaration
        MethodDeclaration decl = findMethod(targetCu, offset);
        IMethodBinding methodBinding = decl.resolveBinding();
        if (methodBinding == null) {
            throw new IllegalArgumentException("Cannot resolve binding for method at offset " + offset + ".");
        }
        String methodKey = methodBinding.getMethodDeclaration().getKey();

        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> params = decl.parameters();
        int paramCount = params.size();

        // Validate paramOrder
        if (paramOrder != null) {
            if (paramOrder.length != paramCount) {
                throw new IllegalArgumentException(
                        "paramOrder length " + paramOrder.length
                        + " does not match parameter count " + paramCount + ".");
            }
            boolean[] seen = new boolean[paramCount];
            for (int idx : paramOrder) {
                if (idx < 0 || idx >= paramCount || seen[idx]) {
                    throw new IllegalArgumentException(
                            "paramOrder is not a valid permutation of [0.." + (paramCount - 1) + "].");
                }
                seen[idx] = true;
            }
            // Check it's not identity
            int[] order = paramOrder;
            if (IntStream.range(0, order.length).allMatch(i -> order[i] == i)) paramOrder = null;
        }

        if (newReturnType == null && paramOrder == null) {
            throw new IllegalArgumentException(
                    "Nothing to change: provide newReturnType, paramOrder, or both.");
        }

        // -------------------------------------------------------------------------
        // Collect edits: Object[] = [start, end, replacement]
        // -------------------------------------------------------------------------
        Map<Path, List<Object[]>> fileEdits = new LinkedHashMap<>();

        String targetSource = sources.get(absTarget);

        // --- Declaration edits (target file only) ---
        List<Object[]> targetEdits = fileEdits.computeIfAbsent(absTarget, k -> new ArrayList<>());

        if (newReturnType != null) {
            Type returnTypeNode = decl.getReturnType2();
            if (returnTypeNode != null) {
                int start = returnTypeNode.getStartPosition();
                int end   = start + returnTypeNode.getLength();
                targetEdits.add(new Object[]{start, end, newReturnType});
            }
        }

        if (paramOrder != null) {
            // Build the new parameter list text
            String[] paramTexts = IntStream.range(0, paramCount)
                    .mapToObj(i -> { var p = params.get(i); return targetSource.substring(p.getStartPosition(), p.getStartPosition() + p.getLength()); })
                    .toArray(String[]::new);
            // Replace the entire parameter span (first param start → last param end)
            int firstStart = params.get(0).getStartPosition();
            SingleVariableDeclaration last = params.get(paramCount - 1);
            int lastEnd = last.getStartPosition() + last.getLength();
            String newParams = Arrays.stream(paramOrder).mapToObj(i -> paramTexts[i]).collect(Collectors.joining(", "));
            targetEdits.add(new Object[]{firstStart, lastEnd, newParams});

            // --- Call site edits (all files) ---
            final int[] finalParamOrder = paramOrder;
            for (Map.Entry<Path, CompilationUnit> entry : cus.entrySet()) {
                Path filePath = entry.getKey();
                CompilationUnit cu = entry.getValue();
                String fileSource = sources.get(filePath);
                List<Object[]> edits = fileEdits.computeIfAbsent(filePath, k -> new ArrayList<>());

                cu.accept(new ASTVisitor() {
                    @Override
                    public boolean visit(MethodInvocation node) {
                        IMethodBinding b = node.resolveMethodBinding();
                        if (b == null || !methodKey.equals(b.getMethodDeclaration().getKey())) return true;
                        @SuppressWarnings("unchecked")
                        List<Expression> args = node.arguments();
                        if (args.size() != paramCount) return true;
                        String[] argTexts = IntStream.range(0, paramCount)
                                .mapToObj(i -> { var arg = args.get(i); return fileSource.substring(arg.getStartPosition(), arg.getStartPosition() + arg.getLength()); })
                                .toArray(String[]::new);
                        int firstArgStart = args.get(0).getStartPosition();
                        Expression lastArg = args.get(paramCount - 1);
                        int lastArgEnd = lastArg.getStartPosition() + lastArg.getLength();
                        String newArgs = Arrays.stream(finalParamOrder).mapToObj(i -> argTexts[i]).collect(Collectors.joining(", "));
                        edits.add(new Object[]{firstArgStart, lastArgEnd, newArgs});
                        return true;
                    }
                });
            }
        }

        // Apply edits per file (end-to-start to keep offsets valid)
        Map<Path, String> changed = new LinkedHashMap<>();
        for (Map.Entry<Path, List<Object[]>> entry : fileEdits.entrySet()) {
            Path filePath = entry.getKey();
            List<Object[]> edits = entry.getValue();
            if (edits.isEmpty()) continue;
            edits.sort((a, b) -> Integer.compare((int) b[0], (int) a[0]));
            StringBuilder sb = new StringBuilder(sources.get(filePath));
            for (Object[] ed : edits) {
                sb.replace((int) ed[0], (int) ed[1], (String) ed[2]);
            }
            changed.put(filePath, sb.toString());
        }
        return changed;
    }

    // -------------------------------------------------------------------------
    // AST helpers
    // -------------------------------------------------------------------------

    private static MethodDeclaration findMethod(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) throw new IllegalArgumentException("No AST node at offset " + offset + ".");
        while (node != null) {
            if (node instanceof MethodDeclaration md) return md;
            node = node.getParent();
        }
        throw new IllegalArgumentException(
                "No method declaration found at offset " + offset + ".");
    }

    // -------------------------------------------------------------------------
    // Infrastructure (same pattern as JdtRemoveParam)
    // -------------------------------------------------------------------------

    private static List<Path> collectSourceFiles(JavaProject project) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.walk(root)) {
                files.addAll(stream.filter(p -> p.toString().endsWith(".java"))
                      .map(p -> p.toAbsolutePath().normalize())
                      .toList());
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
