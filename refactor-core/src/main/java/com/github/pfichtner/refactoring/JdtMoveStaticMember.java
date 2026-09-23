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
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.github.pfichtner.refactoring.project.JavaProject;

/**
 * Headless Move-Static-Member refactoring using JDT ASTParser.
 *
 * <p>Moves a {@code static} method or {@code static} field from one class to another,
 * updating call sites of the form {@code SourceClass.memberName(...)} across all
 * project source files:
 *
 * <pre>{@code
 * // Before — MathUtils.java
 * public class MathUtils {
 *     public static int square(int x) { return x * x; }
 * }
 *
 * // Client.java
 * public class Client {
 *     int v = MathUtils.square(5);
 * }
 *
 * // After: moveStaticMember(project, mathUtils, offset, "Helpers")
 * // MathUtils.java — member removed
 * public class MathUtils {}
 *
 * // Helpers.java — member added
 * public class Helpers {
 *     public static int square(int x) { return x * x; }
 * }
 *
 * // Client.java — call site updated
 * public class Client {
 *     int v = Helpers.square(5);
 * }
 * }</pre>
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No static method or static field at the given offset</li>
 *   <li>The target class source file is not found in the project</li>
 *   <li>The target class already declares a member with the same name</li>
 * </ul>
 *
 * <p>Limitations:
 * <ul>
 *   <li>Call-site matching uses the source class's simple name (as it appears in Java source); members with the same
 *       name in other classes are not affected.</li>
 *   <li>Qualified or wildcard-imported call sites are not updated.</li>
 *   <li>References to the member from within the source class body are not updated.</li>
 * </ul>
 */
public class JdtMoveStaticMember {

    /**
     * Moves the static member at {@code offset} from the class in {@code sourceFile}
     * to the class identified by {@code targetClassName}.
     * Equivalent to {@link #moveStaticMember(JavaProject, Path, int, String, boolean)} with {@code widenVisibility=true}.
     */
    public static Map<Path, String> moveStaticMember(
            JavaProject project, Path sourceFile, int offset, String targetClassName)
            throws IOException, InterruptedException {
        return moveStaticMember(project, sourceFile, offset, targetClassName, true);
    }

    /**
     * Moves the static member at {@code offset} from the class in {@code sourceFile}
     * to the class identified by {@code targetClassName}.
     *
     * @param project         Maven project for enumerating source roots
     * @param sourceFile      file containing the static member to move
     * @param offset          character offset inside the member declaration
     * @param targetClassName fully-qualified name of the target class (e.g. {@code "com.example.Helpers"})
     * @param widenVisibility if {@code true} and the member is {@code private}, its modifier is
     *                        widened to the minimum required: package-private when source and target
     *                        share the same package, {@code public} when they are in different packages
     * @return {@code path → new source} for every changed file
     */
    public static Map<Path, String> moveStaticMember(
            JavaProject project, Path sourceFile, int offset, String targetClassName,
            boolean widenVisibility)
            throws IOException, InterruptedException {

        Path absSource = sourceFile.toAbsolutePath().normalize();
        String sourceText = Files.readString(absSource);
        CompilationUnit sourceCu = parse(sourceText, absSource.getFileName().toString());

        // Locate the static member
        ASTNode node = NodeFinder.perform(sourceCu, offset, 1);
        BodyDeclaration member = findStaticMember(node);
        if (member == null) {
            throw new IllegalArgumentException(
                    "No static method or static field declaration found at offset " + offset + ".");
        }

        String memberName = getMemberName(member);
        String sourceClassName = findPrimaryTypeName(sourceCu);

        // Find target class file
        Path targetFile = JdtPullUpField.findClassFileByFqn(project, targetClassName);
        if (targetFile == null) {
            throw new IllegalArgumentException(
                    "Source file for target class '" + targetClassName
                    + "' not found in project source roots.");
        }

        String targetText = Files.readString(targetFile);
        CompilationUnit targetCu = parse(targetText, targetFile.getFileName().toString());
        TypeDeclaration targetType = JdtPullUpField.findPrimaryType(targetCu);
        String targetSimpleName = targetType.getName().getIdentifier();

        // Check for name collision in target
        for (Object bd : targetType.bodyDeclarations()) {
            if (bd instanceof BodyDeclaration decl) {
                String existing = getMemberName(decl);
                if (memberName.equals(existing)) {
                    throw new IllegalArgumentException(
                            "Target class '" + targetClassName
                            + "' already declares a member named '" + memberName + "'.");
                }
            }
        }

        String rawMember = sourceText.substring(
                member.getStartPosition(), member.getStartPosition() + member.getLength());
        if (widenVisibility) {
            String sourcePackage = JdtPullUpField.packageOf(sourceCu);
            String targetPackage = JdtPullUpField.packageOf(targetCu);
            String widenTo = sourcePackage.equals(targetPackage) ? "" : "public";
            rawMember = JdtPullUpField.widenModifier(member, rawMember, widenTo);
        }

        // Remove from source
        String newSourceText = removeMember(sourceText, member);

        // Insert into target
        String memberIndent = detectIndent(targetText, targetType);
        String newTargetText = insertMember(targetText, targetType, rawMember, memberIndent);

        // Update call sites across all project files
        List<Path> allFiles = collectSourceFiles(project);

        Map<Path, String> result = new LinkedHashMap<>();
        result.put(absSource, newSourceText);
        result.put(targetFile, newTargetText);

        String callSitePattern = sourceClassName + "." + memberName;
        String newCallSite     = targetSimpleName + "." + memberName;

        for (Path f : allFiles) {
            if (f.equals(absSource) || f.equals(targetFile)) continue;
            String fileText = Files.readString(f);
            if (fileText.contains(callSitePattern)) {
                result.put(f, fileText.replace(callSitePattern, newCallSite));
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Text manipulation
    // -------------------------------------------------------------------------

    private static String removeMember(String source, BodyDeclaration member) {
        int start = member.getStartPosition();
        int end   = start + member.getLength();

        // Walk start back to beginning of its line (to include indentation)
        int lineStart = start;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;

        // Walk end forward past the trailing newline
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

    private static String insertMember(
            String targetSource, TypeDeclaration targetType,
            String rawMember, String memberIndent) {

        String reindented = JdtPullUpField.reindent(rawMember, memberIndent);

        // Insert before the closing brace of the target type
        int typeEnd = targetType.getStartPosition() + targetType.getLength();
        int closingBrace = targetSource.lastIndexOf('}', typeEnd - 1);

        String before = targetSource.substring(0, closingBrace).stripTrailing();
        String after  = targetSource.substring(closingBrace);
        return before + "\n\n" + reindented + "\n" + after;
    }

    private static List<Path> collectSourceFiles(JavaProject project)
            throws IOException, InterruptedException {
        List<Path> files = new ArrayList<>();
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.walk(root)) {
                stream.filter(p -> p.toString().endsWith(".java"))
                      .map(p -> p.toAbsolutePath().normalize())
                      .forEach(files::add);
            }
        }
        return files;
    }

    // -------------------------------------------------------------------------
    // AST helpers
    // -------------------------------------------------------------------------

    private static BodyDeclaration findStaticMember(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof MethodDeclaration md && isStatic(md.modifiers())) {
                return md;
            }
            if (current instanceof FieldDeclaration fd && isStatic(fd.modifiers())) {
                return fd;
            }
            current = current.getParent();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static boolean isStatic(List<?> modifiers) {
        for (Object mod : modifiers) {
            if (mod instanceof Modifier m && m.getKeyword() == Modifier.ModifierKeyword.STATIC_KEYWORD) {
                return true;
            }
        }
        return false;
    }

    private static String getMemberName(BodyDeclaration member) {
        if (member == null) return null;
        if (member instanceof MethodDeclaration md) return md.getName().getIdentifier();
        if (member instanceof FieldDeclaration fd) {
            @SuppressWarnings("unchecked")
            List<VariableDeclarationFragment> frags = fd.fragments();
            if (!frags.isEmpty()) return frags.get(0).getName().getIdentifier();
        }
        return null;
    }

    private static String findPrimaryTypeName(CompilationUnit cu) {
        return JdtPullUpField.findPrimaryType(cu).getName().getIdentifier();
    }

    private static String detectIndent(String source, TypeDeclaration type) {
        for (Object bd : type.bodyDeclarations()) {
            if (bd instanceof ASTNode n) {
                int start = n.getStartPosition();
                int lineStart = source.lastIndexOf('\n', start - 1) + 1;
                String prefix = source.substring(lineStart, start);
                if (!prefix.isBlank()) return prefix;
            }
        }
        return "    ";
    }

    // -------------------------------------------------------------------------
    // Parsing
    // -------------------------------------------------------------------------

    private static CompilationUnit parse(String source, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(false);
        return (CompilationUnit) parser.createAST(null);
    }
}
