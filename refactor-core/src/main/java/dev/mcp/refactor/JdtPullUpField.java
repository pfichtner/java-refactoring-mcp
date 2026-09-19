package dev.mcp.refactor;

import dev.mcp.refactor.project.MavenProject;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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
 *   <li>Superclass located by simple name only; fully-qualified {@code extends} clauses
 *       across separate source roots may not resolve.</li>
 *   <li>Multi-fragment declarations (e.g. {@code int x, y;}) are moved as a unit.</li>
 * </ul>
 */
public class JdtPullUpField {

    /**
     * Pulls a field up from a subclass to its direct superclass.
     *
     * @param project    Maven project used to enumerate source roots
     * @param sourceFile file containing the subclass with the field to pull up
     * @param offset     character offset in {@code sourceFile} pointing into the field
     * @return {@code path → new source} for the superclass and the subclass (2 entries)
     */
    public static Map<Path, String> pullUp(
            MavenProject project, Path sourceFile, int offset)
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

        Path superFile = findClassFile(project, superSimpleName);
        if (superFile == null) {
            throw new IllegalArgumentException(
                    "Source file for superclass '" + superSimpleName
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

        String newSubclassSource = removeField(source, field);
        String newSuperSource    = insertField(superSource, superTypeDecl, rawField);

        Map<Path, String> result = new LinkedHashMap<>();
        result.put(superFile, newSuperSource);
        result.put(absSource, newSubclassSource);
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers — also used by JdtPushDownField
    // -------------------------------------------------------------------------

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

    @SuppressWarnings("unchecked")
    static Set<String> fieldNames(FieldDeclaration fd) {
        Set<String> names = new LinkedHashSet<>();
        for (VariableDeclarationFragment frag : (List<VariableDeclarationFragment>) fd.fragments()) {
            names.add(frag.getName().getIdentifier());
        }
        return names;
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
        FieldDeclaration lastField = null;
        for (Object o : targetType.bodyDeclarations()) {
            if (o instanceof FieldDeclaration fd) lastField = fd;
        }

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
        for (Object o : cu.types()) {
            if (o instanceof TypeDeclaration td) return td;
        }
        throw new IllegalArgumentException("No type declaration found in source.");
    }

    static Path findClassFile(MavenProject project, String simpleName)
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
