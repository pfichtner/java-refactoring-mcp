package com.github.pfichtner;

import com.github.pfichtner.project.JavaProject;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Headless Introduce Parameter Object refactoring using JDT ASTParser.
 *
 * <p>Groups two or more contiguous method parameters into a new value-object class:
 * <ol>
 *   <li>Generates the new class with private-final fields, an all-args constructor,
 *       and a getter per field.</li>
 *   <li>Replaces the grouped parameters in the method signature with a single
 *       {@code ClassName paramObjectName} parameter.</li>
 *   <li>Rewrites all references to the original parameters in the method body
 *       to {@code paramObjectName.getXxx()} calls.</li>
 *   <li>Updates every call site in the project: the grouped arguments are wrapped
 *       in {@code new ClassName(arg1, arg2, ...)}.</li>
 * </ol>
 *
 * <p>Uses JDT binding resolution for accurate call-site and parameter-reference matching.
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>Fewer than 2 parameter names specified</li>
 *   <li>A named parameter does not exist in the method</li>
 *   <li>The grouped parameters are not contiguous in the signature</li>
 *   <li>No method found at the given offset</li>
 *   <li>A file named {@code ClassName.java} already exists in the same directory</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Only {@code MethodInvocation} call sites are updated; constructor call sites
 *       of the enclosing class ({@code new EnclosingClass(...)}) are not.</li>
 *   <li>Varargs parameters are not supported.</li>
 *   <li>Bindings may fail to resolve if the project has unresolvable dependencies;
 *       unresolved call sites are silently skipped.</li>
 * </ul>
 */
public class JdtIntroduceParameterObject {

    /**
     * Introduces a parameter object for the specified contiguous parameters.
     *
     * @param project         Maven project providing source roots and classpath
     * @param sourceFile      file containing the method to refactor
     * @param offset          character offset pointing anywhere within the method declaration
     * @param paramNames      names of the parameters to group, in declaration order (≥ 2)
     * @param className       simple name for the new parameter-object class
     * @param paramObjectName name for the new parameter in the refactored method
     * @return {@code path → new source} for every changed file, plus the newly created class
     */
    public static Map<Path, String> introduce(
            JavaProject project,
            Path sourceFile,
            int offset,
            List<String> paramNames,
            String className,
            String paramObjectName)
            throws IOException, InterruptedException {

        if (paramNames.size() < 2) {
            throw new IllegalArgumentException(
                    "At least 2 parameters must be grouped into a parameter object.");
        }

        String[] classpath   = project.classpath();
        String[] sourcePaths = project.sourceRoots().stream()
                .map(Path::toString).toArray(String[]::new);
        List<Path> allFiles  = collectSourceFiles(project);
        Map<Path, String> sources = readAll(allFiles);

        Map<Path, CompilationUnit> cus = parseAll(allFiles, classpath, sourcePaths);
        Path absTarget = sourceFile.toAbsolutePath().normalize();

        CompilationUnit targetCu = cus.get(absTarget);
        if (targetCu == null) {
            throw new IllegalArgumentException(
                    "Source file not found in project: " + sourceFile);
        }

        String targetSource = sources.get(absTarget);

        // Find method at offset
        MethodDeclaration decl = findMethodAt(targetCu, offset);

        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> allParams = decl.parameters();

        // Resolve grouped params in declaration order
        List<SingleVariableDeclaration> groupedParams = new ArrayList<>();
        List<Integer> groupedIndices = new ArrayList<>();
        for (String name : paramNames) {
            boolean found = false;
            for (int i = 0; i < allParams.size(); i++) {
                if (allParams.get(i).getName().getIdentifier().equals(name)) {
                    groupedParams.add(allParams.get(i));
                    groupedIndices.add(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException(
                        "Parameter '" + name + "' not found in method '"
                        + decl.getName().getIdentifier() + "'.");
            }
        }

        // Validate contiguous
        for (int i = 1; i < groupedIndices.size(); i++) {
            if (groupedIndices.get(i) != groupedIndices.get(i - 1) + 1) {
                throw new IllegalArgumentException(
                        "Grouped parameters must be contiguous in the method signature. '"
                        + paramNames.get(i - 1) + "' is at index " + groupedIndices.get(i - 1)
                        + " but '" + paramNames.get(i) + "' is at index " + groupedIndices.get(i) + ".");
            }
        }

        // Check that the new class file does not already exist
        Path newClassFile = absTarget.getParent().resolve(className + ".java");
        if (Files.exists(newClassFile)) {
            throw new IllegalArgumentException(
                    "File '" + className + ".java' already exists in "
                    + absTarget.getParent() + ".");
        }

        // Detect package
        String packageName = targetCu.getPackage() != null
                ? targetCu.getPackage().getName().getFullyQualifiedName()
                : null;

        // Method binding key for call-site matching
        IMethodBinding methodBinding = decl.resolveBinding();
        if (methodBinding == null) {
            throw new IllegalArgumentException(
                    "Cannot resolve binding of the method at the given offset.");
        }
        String methodKey = methodBinding.getMethodDeclaration().getKey();

        // Map param binding key → getter name
        Map<String, String> paramKeyToGetter = new LinkedHashMap<>();
        for (SingleVariableDeclaration svd : groupedParams) {
            IVariableBinding vb = svd.resolveBinding();
            if (vb != null) {
                paramKeyToGetter.put(vb.getKey(), getterName(svd.getName().getIdentifier()));
            }
        }

        // -------------------------------------------------------------------------
        // Generate new class source
        // -------------------------------------------------------------------------
        String newClassSource = buildParameterObjectClass(
                packageName, className, groupedParams, targetSource);

        // -------------------------------------------------------------------------
        // Build edits for the source file
        // -------------------------------------------------------------------------
        List<Object[]> targetEdits = new ArrayList<>();

        // Replace grouped params in signature
        SingleVariableDeclaration firstGrouped = groupedParams.get(0);
        SingleVariableDeclaration lastGrouped  = groupedParams.get(groupedParams.size() - 1);
        targetEdits.add(new Object[]{
                firstGrouped.getStartPosition(),
                lastGrouped.getStartPosition() + lastGrouped.getLength(),
                className + " " + paramObjectName
        });

        // Replace param references in body with paramObjectName.getXxx()
        Block body = decl.getBody();
        if (body != null && !paramKeyToGetter.isEmpty()) {
            body.accept(new ASTVisitor() {
                @Override
                public boolean visit(SimpleName node) {
                    IBinding b = node.resolveBinding();
                    if (!(b instanceof IVariableBinding vb)) return true;
                    String getter = paramKeyToGetter.get(vb.getKey());
                    if (getter == null) return true;
                    targetEdits.add(new Object[]{
                            node.getStartPosition(),
                            node.getStartPosition() + node.getLength(),
                            paramObjectName + "." + getter + "()"
                    });
                    return true;
                }
            });
        }

        // -------------------------------------------------------------------------
        // Update call sites in all project files
        // -------------------------------------------------------------------------
        Map<Path, List<Object[]>> allEdits = new LinkedHashMap<>();
        allEdits.put(absTarget, targetEdits);

        int firstIdx = groupedIndices.get(0);
        int lastIdx  = groupedIndices.get(groupedIndices.size() - 1);

        for (Map.Entry<Path, CompilationUnit> entry : cus.entrySet()) {
            Path filePath = entry.getKey();
            CompilationUnit cu = entry.getValue();
            String fileSrc = sources.get(filePath);
            List<Object[]> fileEdits = allEdits.computeIfAbsent(filePath, k -> new ArrayList<>());

            cu.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation call) {
                    IMethodBinding b = call.resolveMethodBinding();
                    if (b == null) return true;
                    if (!methodKey.equals(b.getMethodDeclaration().getKey())) return true;

                    @SuppressWarnings("unchecked")
                    List<Expression> args = call.arguments();
                    if (args.size() <= lastIdx) return true;

                    fileEdits.add(buildWrapEdit(fileSrc, args, firstIdx, lastIdx, className));
                    return true;
                }
            });
        }

        // -------------------------------------------------------------------------
        // Apply edits
        // -------------------------------------------------------------------------
        Map<Path, String> result = new LinkedHashMap<>();
        for (Map.Entry<Path, List<Object[]>> entry : allEdits.entrySet()) {
            Path filePath = entry.getKey();
            List<Object[]> edits = entry.getValue();
            if (edits.isEmpty()) continue;
            edits.sort((a, b) -> Integer.compare((int) b[0], (int) a[0]));
            StringBuilder sb = new StringBuilder(sources.get(filePath));
            for (Object[] ed : edits) {
                sb.replace((int) ed[0], (int) ed[1], (String) ed[2]);
            }
            result.put(filePath, sb.toString());
        }

        // Add the new class file
        result.put(newClassFile, newClassSource);

        return result;
    }

    // -------------------------------------------------------------------------
    // Source generation
    // -------------------------------------------------------------------------

    private static String buildParameterObjectClass(
            String packageName, String className,
            List<SingleVariableDeclaration> params, String source) {

        StringBuilder sb = new StringBuilder();
        if (packageName != null && !packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        sb.append("public class ").append(className).append(" {\n");

        // Fields
        for (SingleVariableDeclaration p : params) {
            String typeSrc = typeText(source, p);
            sb.append("    private final ").append(typeSrc)
              .append(" ").append(p.getName().getIdentifier()).append(";\n");
        }

        // Constructor
        sb.append("\n    public ").append(className).append("(");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(", ");
            SingleVariableDeclaration p = params.get(i);
            sb.append(typeText(source, p)).append(" ").append(p.getName().getIdentifier());
        }
        sb.append(") {\n");
        for (SingleVariableDeclaration p : params) {
            String name = p.getName().getIdentifier();
            sb.append("        this.").append(name).append(" = ").append(name).append(";\n");
        }
        sb.append("    }\n");

        // Getters (one blank line before the block, no blank lines between getters)
        sb.append("\n");
        for (SingleVariableDeclaration p : params) {
            String name = p.getName().getIdentifier();
            sb.append("    public ").append(typeText(source, p))
              .append(" ").append(getterName(name)).append("() { return ")
              .append(name).append("; }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String typeText(String source, SingleVariableDeclaration p) {
        Type t = p.getType();
        return source.substring(t.getStartPosition(), t.getStartPosition() + t.getLength());
    }

    private static String getterName(String name) {
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // -------------------------------------------------------------------------
    // Edit helpers
    // -------------------------------------------------------------------------

    private static Object[] buildWrapEdit(
            String src, List<Expression> args, int first, int last, String className) {
        StringBuilder text = new StringBuilder("new ").append(className).append("(");
        for (int i = first; i <= last; i++) {
            if (i > first) text.append(", ");
            Expression arg = args.get(i);
            text.append(src, arg.getStartPosition(), arg.getStartPosition() + arg.getLength());
        }
        text.append(")");
        Expression firstArg = args.get(first);
        Expression lastArg  = args.get(last);
        return new Object[]{
                firstArg.getStartPosition(),
                lastArg.getStartPosition() + lastArg.getLength(),
                text.toString()
        };
    }

    // -------------------------------------------------------------------------
    // AST helpers
    // -------------------------------------------------------------------------

    private static MethodDeclaration findMethodAt(CompilationUnit cu, int offset) {
        MethodDeclaration[] found = {null};
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                int start = node.getStartPosition();
                int end   = start + node.getLength();
                if (offset >= start && offset < end) found[0] = node;
                return true;
            }
        });
        if (found[0] == null) {
            throw new IllegalArgumentException(
                    "No method declaration found at the given offset.");
        }
        return found[0];
    }

    // -------------------------------------------------------------------------
    // Infrastructure (mirrors JdtIntroduceParam / JdtRemoveParam)
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
