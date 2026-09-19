package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Headless Introduce Static Factory refactoring using JDT ASTParser.
 *
 * <p>Creates a {@code public static} factory method in the class and rewrites
 * every {@code new ClassName(...)} call site in the project to use the factory.
 * Optionally changes the constructor's visibility to {@code private}.
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No constructor at the given offset</li>
 *   <li>A method with {@code factoryMethodName} and the same parameter count
 *       already exists in the class</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Call-site matching uses the simple class name, so all {@code new ClassName(...)}
 *       in the project are rewritten regardless of which import they resolve to.</li>
 *   <li>Qualified constructor calls ({@code new pkg.ClassName(...)}) are not rewritten.</li>
 * </ul>
 */
public class JdtIntroduceStaticFactory {

    /**
     * Introduces a static factory method for the constructor at {@code offset}.
     *
     * @param project              Maven project used to enumerate source roots
     * @param sourceFile           file containing the class whose constructor to wrap
     * @param offset               character offset in {@code sourceFile} pointing into the constructor
     * @param factoryMethodName    simple name for the new factory method
     * @param makeConstructorPrivate if {@code true}, changes the constructor to {@code private}
     * @return {@code path → new source} for every file that changed
     */
    public static Map<Path, String> introduceStaticFactory(
            MavenProject project,
            Path sourceFile,
            int offset,
            String factoryMethodName,
            boolean makeConstructorPrivate)
            throws IOException, InterruptedException {

        Path absSource = sourceFile.toAbsolutePath().normalize();
        String source = Files.readString(absSource);
        CompilationUnit cu = parse(source, absSource.getFileName().toString());

        MethodDeclaration ctor = findConstructorAt(cu, offset);
        if (ctor == null) {
            throw new IllegalArgumentException(
                    "No constructor found at the given offset.");
        }

        TypeDeclaration type = enclosingType(ctor);
        String className = type.getName().getIdentifier();
        int paramCount = ctor.parameters().size();

        // Reject if a method with the same name and param count already exists
        for (Object o : type.bodyDeclarations()) {
            if (o instanceof MethodDeclaration md
                    && !md.isConstructor()
                    && md.getName().getIdentifier().equals(factoryMethodName)
                    && md.parameters().size() == paramCount) {
                throw new IllegalArgumentException(
                        "Class '" + className + "' already has a method '"
                        + factoryMethodName + "' with " + paramCount + " parameter(s).");
            }
        }

        // Build the factory method text
        String factoryMethod = buildFactoryMethod(source, ctor, className, factoryMethodName);

        // Collect all edits for the source file (applied end→start to preserve offsets)
        List<int[]> callSites = findCallSites(cu, className);
        List<Edit> edits = new ArrayList<>();

        // Call-site replacements inside the source file
        String replacement = className + "." + factoryMethodName;
        for (int[] site : callSites) {
            edits.add(new Edit(site[0], site[1], replacement));
        }

        // Optional: make constructor private
        if (makeConstructorPrivate) {
            Modifier publicMod = findPublicModifier(ctor);
            if (publicMod != null) {
                int mStart = publicMod.getStartPosition();
                edits.add(new Edit(mStart, mStart + publicMod.getLength(), "private"));
            }
        }

        // Insert factory method after the constructor
        int insertAt = ctor.getStartPosition() + ctor.getLength();
        String factoryInsert = "\n\n" + indentBlock(factoryMethod, "    ");
        edits.add(new Edit(insertAt, insertAt, factoryInsert));

        String newSource = applyEdits(source, edits);

        Map<Path, String> result = new LinkedHashMap<>();
        result.put(absSource, newSource);

        // Update call sites in other project source files
        for (Path root : project.sourceRoots()) {
            if (!Files.isDirectory(root)) continue;
            try (var stream = Files.walk(root)) {
                for (Path p : (Iterable<Path>) stream
                        .filter(f -> f.toString().endsWith(".java"))::iterator) {
                    Path absP = p.toAbsolutePath().normalize();
                    if (absP.equals(absSource)) continue;
                    String fileSrc = Files.readString(absP);
                    CompilationUnit fileCu = parse(fileSrc, absP.getFileName().toString());
                    List<int[]> sites = findCallSites(fileCu, className);
                    if (sites.isEmpty()) continue;
                    List<Edit> fileEdits = new ArrayList<>();
                    for (int[] site : sites) {
                        fileEdits.add(new Edit(site[0], site[1], replacement));
                    }
                    result.put(absP, applyEdits(fileSrc, fileEdits));
                }
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Factory method text
    // -------------------------------------------------------------------------

    private static String buildFactoryMethod(
            String source, MethodDeclaration ctor,
            String className, String factoryMethodName) {

        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> params = ctor.parameters();

        String paramList = params.stream()
                .map(p -> source.substring(p.getStartPosition(), p.getStartPosition() + p.getLength()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        String argList = params.stream()
                .map(p -> p.getName().getIdentifier())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        return "public static " + className + " " + factoryMethodName
                + "(" + paramList + ") {\n"
                + "    return new " + className + "(" + argList + ");\n"
                + "}";
    }

    // -------------------------------------------------------------------------
    // Call-site discovery
    // -------------------------------------------------------------------------

    /**
     * Returns [replaceStart, replaceEnd) pairs for each {@code new ClassName(...)}
     * found in the compilation unit, where replaceStart..replaceEnd covers "new ClassName".
     */
    private static List<int[]> findCallSites(CompilationUnit cu, String className) {
        List<int[]> sites = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation node) {
                String typeName = simpleName(node.getType());
                if (!className.equals(typeName)) return true;
                // Replace from "new " through the class name, keeping "(args)"
                int nodeStart = node.getStartPosition();
                Type t = node.getType();
                int typeEnd = t.getStartPosition() + t.getLength();
                sites.add(new int[]{nodeStart, typeEnd});
                return true;
            }
        });
        return sites;
    }

    private static String simpleName(Type type) {
        String raw = type.toString();
        int dot = raw.lastIndexOf('.');
        int lt  = raw.indexOf('<');
        String name = dot >= 0 ? raw.substring(dot + 1) : raw;
        if (lt >= 0) name = name.substring(0, lt);
        return name;
    }

    // -------------------------------------------------------------------------
    // AST helpers
    // -------------------------------------------------------------------------

    private static MethodDeclaration findConstructorAt(CompilationUnit cu, int offset) {
        MethodDeclaration[] found = {null};
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (!node.isConstructor()) return true;
                int start = node.getStartPosition();
                int end   = start + node.getLength();
                if (offset >= start && offset < end) found[0] = node;
                return true;
            }
        });
        return found[0];
    }

    private static TypeDeclaration enclosingType(MethodDeclaration method) {
        if (method.getParent() instanceof TypeDeclaration td) return td;
        throw new IllegalArgumentException(
                "Constructor is not a direct member of a class declaration.");
    }

    private static Modifier findPublicModifier(MethodDeclaration method) {
        for (Object o : method.modifiers()) {
            if (o instanceof Modifier m && m.isPublic()) return m;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Edit application
    // -------------------------------------------------------------------------

    private record Edit(int start, int end, String replacement) {}

    /** Applies edits in descending start-offset order so earlier positions stay valid. */
    private static String applyEdits(String source, List<Edit> edits) {
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        StringBuilder sb = new StringBuilder(source);
        for (Edit e : edits) {
            sb.replace(e.start(), e.end(), e.replacement());
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------

    private static String indentBlock(String text, String indent) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) out.append("\n");
            if (!lines[i].isEmpty()) out.append(indent);
            out.append(lines[i]);
        }
        return out.toString();
    }

    // -------------------------------------------------------------------------
    // Parser
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
