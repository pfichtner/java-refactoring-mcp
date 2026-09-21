package com.github.pfichtner;

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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.github.pfichtner.project.JavaProject;

/**
 * Headless Encapsulate Field refactoring using JDT ASTParser.
 *
 * <p>Makes a public (or package-private) field private, generates a getter and
 * an optional setter, and rewrites all read/write accesses in the project:
 * <ul>
 *   <li>Read: {@code obj.field} → {@code obj.getField()}</li>
 *   <li>Write: {@code obj.field = expr} → {@code obj.setField(expr)}</li>
 *   <li>Declaration: {@code public T field} → {@code private T field}</li>
 * </ul>
 *
 * <p>Precondition failures (diagnostic, no files modified):
 * <ul>
 *   <li>Target node at offset is not a field declaration</li>
 *   <li>Field binding cannot be resolved</li>
 *   <li>Field is already private</li>
 *   <li>Getter or setter with the generated name already exists in the class</li>
 * </ul>
 */
public class JdtEncapsulateField {

    /**
     * Encapsulates the field identified by {@code offset} in {@code sourceFile}.
     *
     * @param project      project providing source roots and classpath
     * @param sourceFile   file containing the field declaration
     * @param offset       character offset within the field name
     * @param generateSetter if {@code true}, a setter is generated in addition to the getter
     * @return map of absolute path → new source for every changed file
     */
    public static Map<Path, String> encapsulateField(
            JavaProject project, Path sourceFile, int offset, boolean generateSetter)
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

        // Locate the field
        VariableDeclarationFragment frag = findField(targetCu, offset);
        IVariableBinding fieldBinding = (IVariableBinding) frag.getName().resolveBinding();
        if (fieldBinding == null) {
            throw new IllegalArgumentException("Cannot resolve binding for field at offset " + offset + ".");
        }
        String fieldKey = fieldBinding.getKey();
        String fieldName = fieldBinding.getName();
        String fieldTypeSrc;

        FieldDeclaration fdecl = (FieldDeclaration) frag.getParent();
        String targetSource = sources.get(absTarget);
        fieldTypeSrc = targetSource.substring(
                fdecl.getType().getStartPosition(),
                fdecl.getType().getStartPosition() + fdecl.getType().getLength());

        // Precondition: must not already be private
        boolean isPrivate = fdecl.modifiers().stream()
                .anyMatch(m -> m instanceof Modifier mod && mod.isPrivate());
        if (isPrivate) {
            throw new IllegalArgumentException("Field '" + fieldName + "' is already private.");
        }

        // Derive accessor names
        String capitalised = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        String getterName = "get" + capitalised;
        String setterName = "set" + capitalised;

        // Precondition: accessor methods must not already exist
        TypeDeclaration enclosingType = enclosingTypeDeclaration(frag);
        if (enclosingType != null) {
            for (Object bd : enclosingType.bodyDeclarations()) {
                if (bd instanceof MethodDeclaration md) {
                    String mn = md.getName().getIdentifier();
                    if (mn.equals(getterName)) {
                        throw new IllegalArgumentException(
                                "Getter '" + getterName + "()' already exists in " + enclosingType.getName().getIdentifier() + ".");
                    }
                    if (generateSetter && mn.equals(setterName)) {
                        throw new IllegalArgumentException(
                                "Setter '" + setterName + "()' already exists in " + enclosingType.getName().getIdentifier() + ".");
                    }
                }
            }
        }

        // -------------------------------------------------------------------------
        // Build edits: Object[] = [start, end, replacement]
        // -------------------------------------------------------------------------
        Map<Path, List<Object[]>> fileEdits = new LinkedHashMap<>();
        List<Object[]> targetEdits = fileEdits.computeIfAbsent(absTarget, k -> new ArrayList<>());

        // 1. Change modifier from public/package-private to private (in declaration)
        replaceModifiersToPrivate(fdecl, targetSource, targetEdits);

        // 2. Insert getter (and optional setter) after the field declaration
        int insertAfter = fdecl.getStartPosition() + fdecl.getLength();
        String accessors = buildAccessors(fieldName, fieldTypeSrc, getterName, setterName, generateSetter);
        targetEdits.add(new Object[]{insertAfter, insertAfter, accessors});

        // 3. Rewrite all access sites across project
        for (Map.Entry<Path, CompilationUnit> entry : cus.entrySet()) {
            Path filePath = entry.getKey();
            CompilationUnit cu = entry.getValue();
            String fileSource = sources.get(filePath);
            List<Object[]> edits = fileEdits.computeIfAbsent(filePath, k -> new ArrayList<>());

            cu.accept(new ASTVisitor() {

                @Override
                public boolean visit(FieldAccess node) {
                    IVariableBinding b = node.resolveFieldBinding();
                    if (b == null || !fieldKey.equals(b.getKey())) return true;
                    rewriteAccess(node, node.getName(), fileSource, edits, getterName, setterName, generateSetter);
                    return false;
                }

                @Override
                public boolean visit(QualifiedName node) {
                    IBinding b = node.getName().resolveBinding();
                    if (!(b instanceof IVariableBinding vb) || !fieldKey.equals(vb.getKey())) return true;
                    rewriteAccess(node, node.getName(), fileSource, edits, getterName, setterName, generateSetter);
                    return false;
                }
            });
        }

        // Apply edits per file (end-to-start)
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
    // Access rewrite helpers
    // -------------------------------------------------------------------------

    private static void rewriteAccess(
            ASTNode fullNode, SimpleName nameNode, String source,
            List<Object[]> edits, String getterName, String setterName, boolean hasSetter) {

        int start = fullNode.getStartPosition();
        int end   = start + fullNode.getLength();
        String receiverText = source.substring(start, nameNode.getStartPosition() - 1); // strip ".field"

        ASTNode parent = fullNode.getParent();

        // Write access: obj.field = expr  →  obj.setter(expr) (only when setter generated)
        if (hasSetter && parent instanceof Assignment assign && assign.getLeftHandSide() == fullNode) {
            Expression rhs = assign.getRightHandSide();
            String rhsText = source.substring(rhs.getStartPosition(), rhs.getStartPosition() + rhs.getLength());
            int assignStart = assign.getStartPosition();
            int assignEnd   = assignStart + assign.getLength();
            edits.add(new Object[]{assignStart, assignEnd, receiverText + "." + setterName + "(" + rhsText + ")"});
            return;
        }

        // Read access: obj.field  →  obj.getter() (write sites without setter are left unchanged)
        if (!(parent instanceof Assignment assign && assign.getLeftHandSide() == fullNode)) {
            edits.add(new Object[]{start, end, receiverText + "." + getterName + "()"});
        }
    }

    // -------------------------------------------------------------------------
    // Modifier replacement
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static void replaceModifiersToPrivate(
            FieldDeclaration fdecl, String source, List<Object[]> edits) {

        List<IExtendedModifier> modifiers = fdecl.modifiers();
        // Find access modifier tokens (public, protected, or none) and replace/insert "private"
        int accessStart = -1, accessEnd = -1;
        for (IExtendedModifier m : modifiers) {
            if (m instanceof Modifier mod &&
                    (mod.isPublic() || mod.isProtected())) {
                accessStart = mod.getStartPosition();
                accessEnd   = accessStart + mod.getLength();
                break;
            }
        }

        if (accessStart >= 0) {
            edits.add(new Object[]{accessStart, accessEnd, "private"});
        } else {
            // package-private: insert "private " before the type
            int typeStart = fdecl.getType().getStartPosition();
            edits.add(new Object[]{typeStart, typeStart, "private "});
        }
    }

    // -------------------------------------------------------------------------
    // Accessor generation
    // -------------------------------------------------------------------------

    private static String buildAccessors(
            String fieldName, String fieldType,
            String getterName, String setterName, boolean generateSetter) {

        String indent = "    ";
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(indent).append("public ").append(fieldType).append(" ").append(getterName).append("() {")
          .append("\n").append(indent).append(indent).append("return ").append(fieldName).append(";")
          .append("\n").append(indent).append("}");
        if (generateSetter) {
            sb.append("\n\n").append(indent).append("public void ").append(setterName)
              .append("(").append(fieldType).append(" ").append(fieldName).append(") {")
              .append("\n").append(indent).append(indent).append("this.").append(fieldName).append(" = ").append(fieldName).append(";")
              .append("\n").append(indent).append("}");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // AST helpers
    // -------------------------------------------------------------------------

    private static VariableDeclarationFragment findField(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) throw new IllegalArgumentException("No AST node at offset " + offset + ".");
        while (node != null) {
            if (node instanceof VariableDeclarationFragment vdf
                    && vdf.getParent() instanceof FieldDeclaration) {
                return vdf;
            }
            node = node.getParent();
        }
        throw new IllegalArgumentException(
                "No field declaration found at offset " + offset + ". Place cursor on a field name.");
    }

    private static TypeDeclaration enclosingTypeDeclaration(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof TypeDeclaration td) return td;
            current = current.getParent();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Infrastructure
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
