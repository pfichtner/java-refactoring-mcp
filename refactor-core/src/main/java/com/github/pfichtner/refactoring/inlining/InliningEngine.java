package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.enclosingMethod;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.findInvocation;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.findMethodDeclaration;
import static org.eclipse.jdt.core.dom.Modifier.isPrivate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;

import com.github.pfichtner.refactoring.JdtProjectSources;
import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Isolated technical spike: iterative inlining of local methods in one
 * compilation unit.
 *
 * <p>Each pass re-parses the rewritten source so bindings and positions are
 * fresh, then applies the first safe candidate. The loop stops at a fixed point
 * (no candidate applies) or when a budget is exhausted.
 */
public final class InliningEngine {

    private InliningEngine() {}

    /** Inlines the call at {@code offset}, or throws if the transformation is unsafe. */
    public static String inlineAt(String source, String unitName, int offset) {
        CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(source, unitName);
        MethodInvocation call = findInvocation(cu, offset);
        IMethodBinding binding = call.resolveMethodBinding();
        if (binding == null) {
            throw new InlineRejectedException("Cannot resolve method call at offset " + offset + ".");
        }
        MethodDeclaration decl = findMethodDeclaration(cu, binding);
        if (decl == null) {
            throw new InlineRejectedException(
                    "Method '" + binding.getName() + "' is not declared in this compilation unit.");
        }
        String reason = SemanticSafetyAnalyzer.rejectionReason(cu, call, decl, binding);
        if (reason != null) {
            throw new InlineRejectedException(reason);
        }
        return tryTransform(cu, source, call, decl, binding);
    }

    public static InliningResult inlineAll(String source, String unitName) {
        return inlineAll(source, unitName, InlineConfig.DEFAULT);
    }

    public static InliningResult inlineAll(
            String source, String unitName, InlineConfig config) {

        String current = source;
        List<InliningResult.Applied> applied = new ArrayList<>();
        List<InliningResult.Rejected> rejected = new ArrayList<>();
        Set<String> rejectedKeys = new HashSet<>();
        Set<String> inlinedKeys = new HashSet<>();
        boolean fixedPoint = false;

        while (applied.size() < config.maxTransformations()) {
            CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(current, unitName);
            CallGraphAnalyzer callGraph = CallGraphAnalyzer.from(cu);
            boolean progress = false;
            boolean budgetBlocked = false;

            for (MethodInvocation call : collectCalls(cu)) {
                IMethodBinding binding = call.resolveMethodBinding();
                if (binding == null) continue;

                MethodDeclaration decl = findMethodDeclaration(cu, binding);
                if (decl == null) continue;

                String methodName = binding.getName();
                int offset = call.getStartPosition();

                // Check mutual recursion before the full semantic analysis.
                String callerKey = callerKey(call);
                if (callerKey != null) {
                    String calleeKey = binding.getMethodDeclaration().getKey();
                    if (callGraph.isMutuallyRecursive(callerKey, calleeKey)) {
                        addRejection(rejected, rejectedKeys, methodName, offset,
                                "mutual recursion detected: inlining would re-invoke the caller");
                        continue;
                    }
                }

                String reason = SemanticSafetyAnalyzer.rejectionReason(cu, call, decl, binding);
                if (reason != null) {
                    addRejection(rejected, rejectedKeys, methodName, offset, reason);
                    continue;
                }

                try {
                    String next = tryTransform(cu, current, call, decl, binding);
                    if (next.equals(current)) {
                        addRejection(rejected, rejectedKeys, methodName, offset,
                                "transformation produced no change");
                        continue;
                    }
                    int growth = next.length() - source.length();
                    if (growth > config.maxCodeGrowthChars()) {
                        budgetBlocked = true;
                        addRejection(rejected, rejectedKeys, methodName, offset,
                                "code growth limit exceeded (" + growth + " > "
                                        + config.maxCodeGrowthChars() + " chars)");
                        continue;
                    }
                    current = next;
                    applied.add(new InliningResult.Applied(methodName, offset));
                    inlinedKeys.add(binding.getMethodDeclaration().getKey());
                    progress = true;
                    break;
                } catch (InlineRejectedException e) {
                    addRejection(rejected, rejectedKeys, methodName, offset, e.getMessage());
                }
            }

            if (!progress) {
                fixedPoint = !budgetBlocked;
                break;
            }
        }

        List<InliningResult.Removed> removed = new ArrayList<>();
        if (fixedPoint && config.removeUnusedDeclarations()) {
            current = removeUnusedDeclarations(current, unitName, inlinedKeys, removed);
        }
        return new InliningResult(current, applied, rejected, removed, fixedPoint);
    }

    public static ProjectInliningResult inlineProject(JavaProject project)
            throws IOException, InterruptedException {
        return inlineProject(project, InlineConfig.DEFAULT);
    }

    public static ProjectInliningResult inlineProject(JavaProject project, InlineConfig config)
            throws IOException, InterruptedException {

        List<Path> files = JdtProjectSources.collectSourceFiles(project);
        Map<Path, String> original = JdtProjectSources.readAll(files);
        Map<Path, String> current = new LinkedHashMap<>(original);
        int originalLength = totalLength(original);

        List<ProjectInliningResult.Applied> applied = new ArrayList<>();
        List<ProjectInliningResult.Rejected> rejected = new ArrayList<>();
        Set<String> rejectedKeys = new HashSet<>();
        Set<String> inlinedKeys = new HashSet<>();
        boolean fixedPoint = false;

        while (applied.size() < config.maxTransformations()) {
            ProjectSnapshot snapshot = ProjectSnapshot.parse(project, current);
            CallGraphAnalyzer callGraph = buildProjectCallGraph(snapshot, files);
            boolean progress = false;
            boolean budgetBlocked = false;

            for (Path file : files) {
                CompilationUnit cu = snapshot.compilationUnit(file);
                if (cu == null) continue;
                String source = current.get(file);
                boolean appliedInFile = false;

                for (MethodInvocation call : snapshot.calls(file)) {
                    IMethodBinding binding = call.resolveMethodBinding();
                    if (binding == null) continue;

                    MethodDeclaration decl = snapshot.declaration(binding);
                    if (decl == null) continue;

                    String methodName = binding.getName();
                    int offset = call.getStartPosition();

                    // Check mutual recursion before the full semantic analysis.
                    String callerKey = callerKey(call);
                    if (callerKey != null) {
                        String calleeKey = binding.getMethodDeclaration().getKey();
                        if (callGraph.isMutuallyRecursive(callerKey, calleeKey)) {
                            addProjectRejection(rejected, rejectedKeys, file, methodName, offset,
                                    "mutual recursion detected: inlining would re-invoke the caller");
                            continue;
                        }
                    }

                    String reason = SemanticSafetyAnalyzer.rejectionReason(cu, call, decl, binding);
                    if (reason != null) {
                        addProjectRejection(rejected, rejectedKeys, file, methodName, offset, reason);
                        continue;
                    }

                    try {
                        String next = tryTransform(cu, source, call, decl, binding);
                        if (next.equals(source)) {
                            addProjectRejection(rejected, rejectedKeys, file, methodName, offset,
                                    "transformation produced no change");
                            continue;
                        }
                        int growth = totalLength(current, file, next) - originalLength;
                        if (growth > config.maxCodeGrowthChars()) {
                            budgetBlocked = true;
                            addProjectRejection(rejected, rejectedKeys, file, methodName, offset,
                                    "code growth limit exceeded (" + growth + " > "
                                            + config.maxCodeGrowthChars() + " chars)");
                            continue;
                        }
                        current.put(file, next);
                        applied.add(new ProjectInliningResult.Applied(file, methodName, offset));
                        inlinedKeys.add(binding.getMethodDeclaration().getKey());
                        progress = true;
                        appliedInFile = true;
                        break;
                    } catch (InlineRejectedException e) {
                        addProjectRejection(rejected, rejectedKeys, file, methodName, offset, e.getMessage());
                    }
                }

                if (appliedInFile && applied.size() >= config.maxTransformations()) break;
            }

            if (!progress) {
                fixedPoint = !budgetBlocked;
                break;
            }
        }

        List<ProjectInliningResult.Removed> removed = new ArrayList<>();
        if (fixedPoint && config.removeUnusedDeclarations()) {
            current = removeUnusedDeclarationsProject(project, files, current, inlinedKeys, removed);
        }

        Map<Path, String> changed = new LinkedHashMap<>();
        for (Path file : files) {
            if (!current.get(file).equals(original.get(file))) {
                changed.put(file, current.get(file));
            }
        }
        return new ProjectInliningResult(changed, applied, rejected, removed, fixedPoint);
    }

    // -------------------------------------------------------------------------
    // Transformation selection
    // -------------------------------------------------------------------------

    /**
     * Tries the hoisting transformation first. When that rejects the call site position,
     * falls back to getter inlining (direct expression substitution), which works at any
     * position. If the getter fallback does not apply, re-throws the hoisting rejection.
     */
    private static String tryTransform(
            CompilationUnit cu, String source,
            MethodInvocation call, MethodDeclaration decl, IMethodBinding binding) {
        try {
            return InlineMethodTransformation.apply(cu, source, call, decl, binding);
        } catch (InlineRejectedException hoistRejection) {
            String getterResult = GetterInliningTransformation.tryApply(cu, source, call, decl, binding);
            if (getterResult != null) return getterResult;
            throw hoistRejection;
        }
    }

    // -------------------------------------------------------------------------
    // Mutual recursion helpers
    // -------------------------------------------------------------------------

    private static String callerKey(MethodInvocation call) {
        MethodDeclaration callerDecl = enclosingMethod(call);
        if (callerDecl == null) return null;
        IMethodBinding callerBinding = callerDecl.resolveBinding();
        if (callerBinding == null) return null;
        return callerBinding.getMethodDeclaration().getKey();
    }

    /** Builds a call graph over all declared methods in the project snapshot. */
    private static CallGraphAnalyzer buildProjectCallGraph(
            ProjectSnapshot snapshot, List<Path> files) {

        Map<String, Set<String>> edges = new HashMap<>();
        for (Path file : files) {
            for (MethodInvocation call : snapshot.calls(file)) {
                IMethodBinding calleeB = call.resolveMethodBinding();
                if (calleeB == null) continue;
                String calleeKey = calleeB.getMethodDeclaration().getKey();
                if (snapshot.declaration(calleeB) == null) continue; // callee not a candidate

                MethodDeclaration callerDecl = enclosingMethod(call);
                if (callerDecl == null) continue;
                IMethodBinding callerB = callerDecl.resolveBinding();
                if (callerB == null) continue;
                String callerKey = callerB.getMethodDeclaration().getKey();

                edges.computeIfAbsent(callerKey, k -> new HashSet<>()).add(calleeKey);
            }
        }
        return new CallGraphAnalyzer(edges);
    }

    // -------------------------------------------------------------------------
    // Declaration removal
    // -------------------------------------------------------------------------

    /**
     * After the fixed point, removes private method declarations whose binding key
     * was inlined and that no longer have any call sites in the compilation unit.
     */
    private static String removeUnusedDeclarations(
            String source, String unitName,
            Set<String> inlinedKeys,
            List<InliningResult.Removed> removed) {

        if (inlinedKeys.isEmpty()) return source;

        CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(source, unitName);

        Set<String> calledKeys = new HashSet<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                IMethodBinding b = node.resolveMethodBinding();
                if (b != null) calledKeys.add(b.getMethodDeclaration().getKey());
                return true;
            }
        });

        List<MethodDeclaration> toRemove = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                IMethodBinding b = node.resolveBinding();
                if (b == null) return false;
                String key = b.getMethodDeclaration().getKey();
                if (inlinedKeys.contains(key) && !calledKeys.contains(key)
                        && isPrivate(b.getModifiers())) {
                    toRemove.add(node);
                    removed.add(new InliningResult.Removed(b.getName()));
                }
                return false;
            }
        });

        if (toRemove.isEmpty()) return source;

        AST ast = cu.getAST();
        ASTRewrite rewrite = ASTRewrite.create(ast);
        for (MethodDeclaration method : toRemove) {
            if (method.getParent() instanceof TypeDeclaration td) {
                ListRewrite lr = rewrite.getListRewrite(td, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
                lr.remove(method, null);
            }
        }
        return applyRewrite(source, cu, rewrite);
    }

    /**
     * Project-wide equivalent of {@link #removeUnusedDeclarations}: removes private
     * method declarations that were inlined and have no remaining call sites anywhere
     * in the project.
     */
    private static Map<Path, String> removeUnusedDeclarationsProject(
            JavaProject project, List<Path> files, Map<Path, String> current,
            Set<String> inlinedKeys,
            List<ProjectInliningResult.Removed> removed)
            throws IOException, InterruptedException {

        if (inlinedKeys.isEmpty()) return current;

        ProjectSnapshot finalSnapshot = ProjectSnapshot.parse(project, current);

        // Collect all remaining call sites across the project.
        Set<String> calledKeys = new HashSet<>();
        for (Path file : files) {
            for (MethodInvocation call : finalSnapshot.calls(file)) {
                IMethodBinding b = call.resolveMethodBinding();
                if (b != null) calledKeys.add(b.getMethodDeclaration().getKey());
            }
        }

        Map<Path, String> result = new LinkedHashMap<>(current);
        for (Path file : files) {
            CompilationUnit cu = finalSnapshot.compilationUnit(file);
            if (cu == null) continue;

            List<MethodDeclaration> toRemove = new ArrayList<>();
            cu.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodDeclaration node) {
                    IMethodBinding b = node.resolveBinding();
                    if (b == null) return false;
                    String key = b.getMethodDeclaration().getKey();
                    if (inlinedKeys.contains(key) && !calledKeys.contains(key)
                            && isPrivate(b.getModifiers())) {
                        toRemove.add(node);
                        removed.add(new ProjectInliningResult.Removed(file, b.getName()));
                    }
                    return false;
                }
            });

            if (toRemove.isEmpty()) continue;

            String source = current.get(file);
            AST ast = cu.getAST();
            ASTRewrite rewrite = ASTRewrite.create(ast);
            for (MethodDeclaration method : toRemove) {
                if (method.getParent() instanceof TypeDeclaration td) {
                    ListRewrite lr = rewrite.getListRewrite(td, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
                    lr.remove(method, null);
                }
            }
            result.put(file, applyRewrite(source, cu, rewrite));
        }
        return result;
    }

    private static String applyRewrite(String source, CompilationUnit cu, ASTRewrite rewrite) {
        Document doc = new Document(source);
        try {
            rewrite.rewriteAST(doc, null).apply(doc);
        } catch (BadLocationException e) {
            throw new IllegalStateException("Declaration removal failed", e);
        }
        return doc.get();
    }

    // -------------------------------------------------------------------------
    // Shared utilities
    // -------------------------------------------------------------------------

    private static int totalLength(Map<Path, String> sources) {
        int total = 0;
        for (String source : sources.values()) total += source.length();
        return total;
    }

    private static int totalLength(Map<Path, String> sources, Path replaced, String replacement) {
        int total = replacement.length();
        for (Map.Entry<Path, String> entry : sources.entrySet()) {
            if (!entry.getKey().equals(replaced)) total += entry.getValue().length();
        }
        return total;
    }

    private static void addProjectRejection(
            List<ProjectInliningResult.Rejected> rejected, Set<String> keys,
            Path path, String methodName, int offset, String reason) {

        if (keys.add(path + " " + methodName + " " + reason)) {
            rejected.add(new ProjectInliningResult.Rejected(path, methodName, offset, reason));
        }
    }

    /**
     * One parsed pass over all current sources. Sources are copied to a temporary
     * tree so JDT can resolve bindings across files without writing to the project.
     */
    private static final class ProjectSnapshot {

        private final Map<Path, CompilationUnit> cus;
        private final Map<Path, List<MethodInvocation>> calls;
        private final Map<String, MethodDeclaration> declarations;

        private ProjectSnapshot(
                Map<Path, CompilationUnit> cus,
                Map<Path, List<MethodInvocation>> calls,
                Map<String, MethodDeclaration> declarations) {
            this.cus = cus;
            this.calls = calls;
            this.declarations = declarations;
        }

        static ProjectSnapshot parse(JavaProject project, Map<Path, String> sources)
                throws IOException, InterruptedException {

            List<Path> roots = project.sourceRoots();
            Path tempRoot = Files.createTempDirectory("inline-project-");
            try {
                Map<Path, Path> tempToOriginal = new LinkedHashMap<>();
                List<Path> tempFiles = new ArrayList<>();
                List<Path> tempRoots = new ArrayList<>();
                for (int i = 0; i < roots.size(); i++) {
                    Path temp = tempRoot.resolve("root" + i);
                    Files.createDirectories(temp);
                    tempRoots.add(temp);
                }
                Path fallback = tempRoot.resolve("other");
                Files.createDirectories(fallback);
                boolean usedFallback = false;

                for (Map.Entry<Path, String> entry : sources.entrySet()) {
                    Path original = entry.getKey().toAbsolutePath().normalize();
                    Path relative = original.getFileName();
                    int rootIndex = -1;
                    for (int i = 0; i < roots.size(); i++) {
                        Path root = roots.get(i).toAbsolutePath().normalize();
                        if (original.startsWith(root)) {
                            rootIndex = i;
                            relative = root.relativize(original);
                            break;
                        }
                    }
                    Path tempSourceRoot;
                    if (rootIndex >= 0) {
                        tempSourceRoot = tempRoot.resolve("root" + rootIndex);
                    } else {
                        tempSourceRoot = fallback;
                        usedFallback = true;
                    }
                    Path tempFile = tempSourceRoot.resolve(relative);
                    Files.createDirectories(tempFile.getParent());
                    Files.writeString(tempFile, entry.getValue());
                    Path normalizedTemp = tempFile.toAbsolutePath().normalize();
                    tempFiles.add(normalizedTemp);
                    tempToOriginal.put(normalizedTemp, entry.getKey());
                }
                if (usedFallback) tempRoots.add(fallback);

                String[] classpath = project.classpath();
                String[] sourcePaths = tempRoots.stream().map(Path::toString).toArray(String[]::new);
                Map<Path, CompilationUnit> parsed =
                        JdtProjectSources.parseAll(tempFiles, classpath, sourcePaths);

                Map<Path, CompilationUnit> cus = new LinkedHashMap<>();
                for (Map.Entry<Path, CompilationUnit> entry : parsed.entrySet()) {
                    Path original = tempToOriginal.get(
                            entry.getKey().toAbsolutePath().normalize());
                    if (original != null) cus.put(original, entry.getValue());
                }

                Map<Path, List<MethodInvocation>> calls = new LinkedHashMap<>();
                Map<String, MethodDeclaration> declarations = new LinkedHashMap<>();
                for (Path file : sources.keySet()) {
                    CompilationUnit cu = cus.get(file);
                    List<MethodInvocation> fileCalls = new ArrayList<>();
                    if (cu != null) {
                        cu.accept(new ASTVisitor() {
                            @Override
                            public boolean visit(MethodInvocation node) {
                                fileCalls.add(node);
                                return true;
                            }

                            @Override
                            public boolean visit(MethodDeclaration node) {
                                IMethodBinding binding = node.resolveBinding();
                                if (binding != null) {
                                    declarations.putIfAbsent(
                                            binding.getMethodDeclaration().getKey(), node);
                                }
                                return true;
                            }
                        });
                    }
                    Collections.reverse(fileCalls);
                    calls.put(file, fileCalls);
                }
                return new ProjectSnapshot(cus, calls, declarations);
            } finally {
                deleteRecursively(tempRoot);
            }
        }

        CompilationUnit compilationUnit(Path file) {
            return cus.get(file);
        }

        List<MethodInvocation> calls(Path file) {
            return calls.getOrDefault(file, List.of());
        }

        MethodDeclaration declaration(IMethodBinding binding) {
            return declarations.get(binding.getMethodDeclaration().getKey());
        }
    }

    private static void deleteRecursively(Path root) {
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort temp cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort temp cleanup
        }
    }

    private static List<MethodInvocation> collectCalls(CompilationUnit cu) {
        List<MethodInvocation> calls = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                calls.add(node);
                return true;
            }
        });
        Collections.reverse(calls);
        return calls;
    }

    private static void addRejection(
            List<InliningResult.Rejected> rejected, Set<String> keys,
            String methodName, int offset, String reason) {

        if (keys.add(methodName + ' ' + reason)) {
            rejected.add(new InliningResult.Rejected(methodName, offset, reason));
        }
    }
}
