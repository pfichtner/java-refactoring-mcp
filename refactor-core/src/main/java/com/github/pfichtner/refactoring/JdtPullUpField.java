package com.github.pfichtner.refactoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Headless Pull-Up Field refactoring using JDT ASTParser.
 *
 * <p>Moves a field declaration from a subclass to its direct superclass.
 * The superclass must exist within the project source roots.
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No field at the given offset</li>
 *   <li>Enclosing class has no explicit {@code extends} clause</li>
 *   <li>Superclass source not found in project</li>
 *   <li>Superclass already declares a field with the same name</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Superclass resolved via import declarations first, then same-package assumption;
 *       wildcard imports ({@code import pkg.*}) are not resolved.</li>
 *   <li>Multi-fragment declarations (e.g. {@code int x, y;}) are moved as a unit.</li>
 * </ul>
 */
public class JdtPullUpField {

    /**
     * Pulls a field up from a subclass to its direct superclass.
     * Equivalent to {@link #pullUp(JavaProject, Path, int, boolean)} with {@code widenVisibility=true}.
     */
    public static Map<Path, String> pullUp(
            JavaProject project, Path sourceFile, int offset)
            throws IOException, InterruptedException {
        return pullUp(project, sourceFile, offset, true);
    }

    /**
     * Pulls a field up from a subclass to its direct superclass.
     *
     * @param project         Maven project used to enumerate source roots
     * @param sourceFile      file containing the subclass with the field to pull up
     * @param offset          character offset in {@code sourceFile} pointing into the field
     * @param widenVisibility if {@code true} and the field is {@code private}, its modifier
     *                        is changed to {@code protected} in the superclass
     * @return {@code path → new source} for the superclass and the subclass (2 entries)
     */
    public static Map<Path, String> pullUp(
            JavaProject project, Path sourceFile, int offset, boolean widenVisibility)
            throws IOException, InterruptedException {

        Path absSource = sourceFile.toAbsolutePath().normalize();
        String source = Files.readString(absSource);
        CompilationUnit cu = parse(source, absSource.getFileName().toString());

        FieldDeclaration field = findFieldAt(cu, offset);
        if (field == null) {
            throw new IllegalArgumentException(
                    "No field declaration found at the given offset.");
        }

        TypeDeclaration type = enclosingType(field);
        Type superType = type.getSuperclassType();
        if (superType == null) {
            throw new IllegalArgumentException(
                    "Class '" + type.getName().getIdentifier()
                    + "' has no explicit superclass — cannot pull up.");
        }
        String superSimpleName = extractSimpleName(superType);
        String superFqn = resolveClassFqn(cu, superSimpleName);

        Path superFile = findClassFileByFqn(project, superFqn);
        if (superFile == null) superFile = findClassFile(project, superSimpleName);
        if (superFile == null) {
            throw new IllegalArgumentException(
                    "Source file for superclass '" + superFqn
                    + "' not found in project source roots.");
        }

        // Check for duplicate field names in superclass
        String superSource = Files.readString(superFile);
        CompilationUnit superCu = parse(superSource, superFile.getFileName().toString());
        TypeDeclaration superTypeDecl = findPrimaryType(superCu);

        Set<String> fieldNames = fieldNames(field);
        for (Object o : superTypeDecl.bodyDeclarations()) {
            if (o instanceof FieldDeclaration fd) {
                for (String name : fieldNames(fd)) {
                    if (fieldNames.contains(name)) {
                        throw new IllegalArgumentException(
                                "Superclass '" + superSimpleName
                                + "' already declares a field '" + name + "'.");
                    }
                }
            }
        }

        String rawField = source.substring(
                field.getStartPosition(), field.getStartPosition() + field.getLength());
        if (widenVisibility) rawField = widenModifier(field, rawField, "protected");

        String newSubclassSource = removeField(source, field);
        String newSuperSource    = insertField(superSource, superTypeDecl, rawField);

        Map<Path, String> result = new LinkedHashMap<>();
        result.put(superFile, newSuperSource);
        result.put(absSource, newSubclassSource);
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers — also used by JdtPushDownField and other move refactorings
    // -------------------------------------------------------------------------

    /**
     * If {@code node} has a {@code private} modifier, replaces it with {@code newModifier}
     * in the raw source text {@code rawText} (which starts at {@code node.getStartPosition()}).
     * Uses the modifier's AST node positions for the edit.  Pass {@code ""} for {@code newModifier}
     * to make the member package-private (the trailing space after {@code private} is also consumed).
     * Returns {@code rawText} unchanged when no {@code private} modifier is present.
     */
    static String widenModifier(BodyDeclaration node, String rawText, String newModifier) {
        for (Object mod : node.modifiers()) {
            if (mod instanceof Modifier m && m.isPrivate()) {
                int relStart = m.getStartPosition() - node.getStartPosition();
                int relEnd   = relStart + m.getLength();
                if (newModifier.isEmpty() && relEnd < rawText.length() && rawText.charAt(relEnd) == ' ') {
                    relEnd++; // consume the space that followed "private" so we don't leave a leading gap
                }
                return rawText.substring(0, relStart)
                        + newModifier
                        + rawText.substring(relEnd);
            }
        }
        return rawText;
    }

    /** Returns the package name of the compilation unit, or {@code ""} for the default package. */
    static String packageOf(CompilationUnit cu) {
        PackageDeclaration pkg = cu.getPackage();
        return pkg != null ? pkg.getName().getFullyQualifiedName() : "";
    }

    static FieldDeclaration findFieldAt(CompilationUnit cu, int offset) {
        FieldDeclaration[] found = {null};
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(FieldDeclaration node) {
                if (!(node.getParent() instanceof TypeDeclaration parent)) return true;
                if (!(parent.getParent() instanceof CompilationUnit)) return true;
                int start = node.getStartPosition();
                int end   = start + node.getLength();
                if (offset >= start && offset < end) found[0] = node;
                return true;
            }
        });
        return found[0];
    }

    static TypeDeclaration enclosingType(FieldDeclaration field) {
        if (field.getParent() instanceof TypeDeclaration td) return td;
        throw new IllegalArgumentException(
                "Field is not a direct member of a class declaration.");
    }

    static Set<String> fieldNames(FieldDeclaration fd) {
        return ((List<?>) fd.fragments()).stream()
                .map(o -> ((VariableDeclarationFragment) o).getName().getIdentifier())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    static String removeField(String source, FieldDeclaration field) {
        int start = field.getStartPosition();
        int end   = start + field.getLength();

        // Walk start back to the beginning of its line
        int lineStart = start;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;

        // Walk end forward to consume the trailing newline
        while (end < source.length() && source.charAt(end) != '\n') end++;
        if (end < source.length()) end++;

        // Consume one preceding blank line if present
        if (lineStart >= 2
                && source.charAt(lineStart - 1) == '\n'
                && source.charAt(lineStart - 2) == '\n') {
            lineStart--;
        }

        return source.substring(0, lineStart) + source.substring(end);
    }

    static String insertField(String targetSource, TypeDeclaration targetType, String rawField) {
        // Insert after the last existing field, or just inside the opening brace if none
        FieldDeclaration lastField = ((List<?>) targetType.bodyDeclarations()).stream()
                .filter(o -> o instanceof FieldDeclaration)
                .map(o -> (FieldDeclaration) o)
                .reduce((first, second) -> second)
                .orElse(null);

        String reindented = reindent(rawField, "    ");

        if (lastField != null) {
            int insertAt = lastField.getStartPosition() + lastField.getLength();
            String before = targetSource.substring(0, insertAt);
            String after  = targetSource.substring(insertAt);
            // 'after' already begins with a newline (or blank line), so don't add another
            return before.stripTrailing() + "\n" + reindented + after;
        } else {
            int typeStart = targetType.getStartPosition();
            int openBrace = targetSource.indexOf('{', typeStart);
            String before = targetSource.substring(0, openBrace + 1);
            String after  = targetSource.substring(openBrace + 1);
            return before + "\n" + reindented + "\n" + after;
        }
    }

    static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        return ((List<?>) cu.types()).stream()
                .filter(o -> o instanceof TypeDeclaration)
                .map(o -> (TypeDeclaration) o)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No type declaration found in source."));
    }

    /**
     * Looks up a class file by its fully-qualified name (e.g. {@code "com.example.Report"}).
     * Converts the FQN to a relative path and checks each source root for an exact match.
     */
    static Path findClassFileByFqn(JavaProject project, String fqn)
            throws IOException, InterruptedException {
        String relativePath = fqn.replace('.', '/') + ".java";
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            Path candidate = root.resolve(relativePath).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    /**
     * Resolves a simple class name to its fully-qualified name by inspecting the
     * import declarations of the given compilation unit.  If no matching import is
     * found, the class is assumed to be in the same package as the compilation unit.
     */
    static String resolveClassFqn(CompilationUnit cu, String simpleName) {
        for (Object o : cu.imports()) {
            if (o instanceof ImportDeclaration id && !id.isStatic() && !id.isOnDemand()) {
                String fqn = id.getName().getFullyQualifiedName();
                if (fqn.endsWith("." + simpleName)) return fqn;
            }
        }
        PackageDeclaration pkg = cu.getPackage();
        return pkg != null ? pkg.getName().getFullyQualifiedName() + "." + simpleName : simpleName;
    }

    /**
     * Returns the fully-qualified name of the primary type declared in the given
     * compilation unit (package name + "." + simple type name).
     */
    static String primaryTypeFqn(CompilationUnit cu) {
        String simpleName = findPrimaryType(cu).getName().getIdentifier();
        PackageDeclaration pkg = cu.getPackage();
        return pkg != null ? pkg.getName().getFullyQualifiedName() + "." + simpleName : simpleName;
    }

    /**
     * Returns {@code true} if one of the top-level type declarations in {@code cu}
     * has a superclass whose FQN matches {@code superclassFqn}.  Both the import
     * list and the package declaration are consulted to resolve simple names.
     */
    static boolean extendsClass(CompilationUnit cu, String superclassFqn) {
        String superSimpleName = superclassFqn.substring(superclassFqn.lastIndexOf('.') + 1);
        String superPackage    = superclassFqn.contains(".")
                ? superclassFqn.substring(0, superclassFqn.lastIndexOf('.')) : "";
        for (Object o : cu.types()) {
            if (!(o instanceof TypeDeclaration td)) continue;
            Type superType = td.getSuperclassType();
            if (superType == null) continue;
            String typeName   = superType.toString();
            int dot           = typeName.lastIndexOf('.');
            String simpleExt  = dot >= 0 ? typeName.substring(dot + 1) : typeName;
            if (!simpleExt.equals(superSimpleName)) continue;
            // Fully-qualified in the extends clause?
            if (typeName.equals(superclassFqn)) return true;
            // Same package?
            PackageDeclaration pkg = cu.getPackage();
            if (pkg != null && pkg.getName().getFullyQualifiedName().equals(superPackage)) return true;
            // Explicit import?
            for (Object imp : cu.imports()) {
                if (imp instanceof ImportDeclaration id && !id.isStatic() && !id.isOnDemand()
                        && id.getName().getFullyQualifiedName().equals(superclassFqn)) return true;
            }
        }
        return false;
    }

    static Path findClassFile(JavaProject project, String simpleName)
            throws IOException, InterruptedException {
        String fileName = simpleName + ".java";
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.walk(root)) {
                Optional<Path> found = stream
                        .filter(p -> p.getFileName().toString().equals(fileName))
                        .findFirst();
                if (found.isPresent()) return found.get().toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private static String extractSimpleName(Type type) {
        String name = type.toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    static String reindent(String text, String newIndent) {
        String[] lines = text.split("\n", -1);
        if (lines.length == 0) return newIndent;

        int baseIndent = Integer.MAX_VALUE;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                int sp = 0;
                while (sp < lines[i].length() && lines[i].charAt(sp) == ' ') sp++;
                baseIndent = Math.min(baseIndent, sp);
            }
        }
        if (baseIndent == Integer.MAX_VALUE) baseIndent = 0;

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i == 0) {
                out.append(newIndent).append(line.stripLeading());
            } else if (line.isBlank()) {
                out.append("");
            } else {
                String stripped = line.length() >= baseIndent ? line.substring(baseIndent) : line;
                out.append(newIndent).append(stripped);
            }
            if (i < lines.length - 1) out.append("\n");
        }
        return out.toString();
    }

    static CompilationUnit parse(String source, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(false);
        return (CompilationUnit) parser.createAST(null);
    }
}
