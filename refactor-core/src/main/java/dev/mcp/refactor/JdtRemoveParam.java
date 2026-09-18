package dev.mcp.refactor;

import dev.mcp.refactor.project.MavenProject;
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
            MavenProject project, Path sourceFile, int offset)
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

        // -------------------------------------------------------------------------
        // Build edits
        // -------------------------------------------------------------------------
        Map<Path, List<int[]>> fileEdits = new LinkedHashMap<>(); // [start, end] to delete

        // Target file: remove the parameter from the declaration
        List<int[]> targetEdits = fileEdits.computeIfAbsent(absTarget, k -> new ArrayList<>());
        targetEdits.add(paramRemoveRange(targetSource, params, paramIndex));

        // All files: remove the argument at paramIndex from each matching call site
        for (Map.Entry<Path, CompilationUnit> entry : cus.entrySet()) {
            Path filePath = entry.getKey();
            CompilationUnit cu = entry.getValue();
            String fileSource = sources.get(filePath);
            List<int[]> edits = fileEdits.computeIfAbsent(filePath, k -> new ArrayList<>());

            cu.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation call) {
                    IMethodBinding b = call.resolveMethodBinding();
                    if (b == null) return true;
                    if (!methodKey.equals(b.getMethodDeclaration().getKey())) return true;
                    @SuppressWarnings("unchecked") List<Expression> args = call.arguments();
                    if (paramIndex >= args.size()) return true; // already removed / mismatch
                    edits.add(argRemoveRange(fileSource, args, paramIndex));
                    return true;
                }
            });
        }

        // Apply edits per file (end-to-start within each file)
        Map<Path, String> changed = new LinkedHashMap<>();
        for (Map.Entry<Path, List<int[]>> entry : fileEdits.entrySet()) {
            Path filePath = entry.getKey();
            List<int[]> edits = entry.getValue();
            if (edits.isEmpty()) continue;

            edits.sort((a, b) -> b[0] - a[0]);
            StringBuilder sb = new StringBuilder(sources.get(filePath));
            for (int[] ed : edits) {
                sb.delete(ed[0], ed[1]);
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

    private static List<Path> collectSourceFiles(MavenProject project) throws IOException {
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
