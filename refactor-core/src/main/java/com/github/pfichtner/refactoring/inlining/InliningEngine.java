package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.findInvocation;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.findMethodDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;

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
        return InlineMethodTransformation.apply(cu, source, call, decl, binding);
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
        boolean fixedPoint = false;

        while (applied.size() < config.maxTransformations()) {
            CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(current, unitName);
            boolean progress = false;
            boolean budgetBlocked = false;

            for (MethodInvocation call : collectCalls(cu)) {
                IMethodBinding binding = call.resolveMethodBinding();
                if (binding == null) continue;

                MethodDeclaration decl = findMethodDeclaration(cu, binding);
                if (decl == null) continue;

                String methodName = binding.getName();
                int offset = call.getStartPosition();
                String reason = SemanticSafetyAnalyzer.rejectionReason(cu, call, decl, binding);
                if (reason != null) {
                    addRejection(rejected, rejectedKeys, methodName, offset, reason);
                    continue;
                }

                try {
                    String next = InlineMethodTransformation.apply(cu, current, call, decl, binding);
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

        return new InliningResult(current, applied, rejected, fixedPoint);
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
        boolean fixedPoint = false;

        while (applied.size() < config.maxTransformations()) {
            ProjectSnapshot snapshot = ProjectSnapshot.parse(project, current);
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
                    String reason = SemanticSafetyAnalyzer.rejectionReason(cu, call, decl, binding);
                    if (reason != null) {
                        addProjectRejection(rejected, rejectedKeys, file, methodName, offset, reason);
                        continue;
                    }

                    try {
                        String next = InlineMethodTransformation.apply(cu, source, call, decl, binding);
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

        Map<Path, String> changed = new LinkedHashMap<>();
        for (Path file : files) {
            if (!current.get(file).equals(original.get(file))) {
                changed.put(file, current.get(file));
            }
        }
        return new ProjectInliningResult(changed, applied, rejected, fixedPoint);
    }

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

        if (keys.add(path + "\u0000" + methodName + "\u0000" + reason)) {
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

        if (keys.add(methodName + '\u0000' + reason)) {
            rejected.add(new InliningResult.Rejected(methodName, offset, reason));
        }
    }
}
