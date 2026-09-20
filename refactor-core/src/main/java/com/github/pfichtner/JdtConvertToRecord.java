package com.github.pfichtner;

import com.github.pfichtner.project.JavaProject;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Headless Convert-Class-to-Record refactoring using JDT ASTParser.
 *
 * <p>Converts a simple data class into a Java record declaration (Java 16+):
 * <ol>
 *   <li>The {@code private final} fields become record components.</li>
 *   <li>The all-args constructor is removed (records auto-generate it).</li>
 *   <li>Simple bean-style accessors ({@code getX()}) and record-style accessors ({@code x()})
 *       that just {@code return fieldName;} are removed, since records auto-generate
 *       {@code fieldName()} accessors.</li>
 *   <li>Bean-style getter call sites ({@code obj.getX()}) are renamed to the record accessor
 *       form ({@code obj.x()}) across all project files. Binding resolution is used for
 *       other-file renames; name-based matching is used for the converted class itself.</li>
 *   <li>All other methods, {@code implements} clauses, and annotations are preserved.</li>
 * </ol>
 *
 * <p>The tool generates valid record syntax; the output requires Java 16+ to compile.
 * The tool itself runs on any JVM version supported by the JDT dependency.
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>Source is an interface, enum, or already a record</li>
 *   <li>Class is {@code abstract}</li>
 *   <li>Class has an explicit {@code extends} clause (records cannot extend classes)</li>
 *   <li>Class has no {@code private final} fields</li>
 *   <li>No all-args constructor found covering every {@code private final} field</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Multi-fragment field declarations ({@code private final int x, y;}) are supported.</li>
 *   <li>Accessor detection matches bean-style ({@code getX()}) and record-style ({@code x()})
 *       naming only; other conventions are left in the record body.</li>
 *   <li>Call-site renaming in the converted class's own retained methods uses name-based
 *       matching; false positives are possible if a retained method calls a same-named
 *       getter on an unrelated type.</li>
 *   <li>Generic type parameters on the class are preserved verbatim.</li>
 * </ul>
 */
public class JdtConvertToRecord {

    /**
     * Converts the primary class in {@code sourceFile} to a record and renames
     * bean-style getter call sites across the project.
     *
     * @param project    Maven project for enumerating source roots and classpath
     * @param sourceFile file containing the class to convert
     * @return {@code path → new source} for every file that changed
     * @throws IllegalArgumentException if the class does not satisfy the preconditions
     */
    public static Map<Path, String> convertToRecord(JavaProject project, Path sourceFile)
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
            throw new IllegalArgumentException(
                    "Source file not found in project: " + sourceFile);
        }

        String source = sources.get(absTarget);
        TypeDeclaration type = findPrimaryType(targetCu);
        validatePreconditions(source, type);

        List<FieldComponent> components = collectComponents(source, type);
        MethodDeclaration ctor = findAllArgsConstructor(source, type, components);
        if (ctor == null) {
            throw new IllegalArgumentException(
                    "Class '" + type.getName().getIdentifier()
                    + "' has no all-args constructor that assigns every private final field. "
                    + "Expected " + components.size() + " parameter(s) setting: "
                    + components.stream().map(c -> c.name).collect(Collectors.joining(", ")) + ".");
        }

        Set<String> fieldNames = components.stream().map(c -> c.name).collect(Collectors.toSet());
        Set<MethodDeclaration> accessors = collectSimpleAccessors(source, type, fieldNames);

        // For each removed bean-style getter, build:
        //   methodBindingKey → fieldName  (for binding-based cross-file renaming)
        //   methodName       → fieldName  (for name-based self-renaming in the converted file)
        Map<String, String> getterKeyToFieldName  = new LinkedHashMap<>();
        Map<String, String> beanNameToFieldName   = new LinkedHashMap<>();
        for (MethodDeclaration getter : accessors) {
            String methodName = getter.getName().getIdentifier();
            if (methodName.startsWith("get") && methodName.length() > 3
                    && Character.isUpperCase(methodName.charAt(3))) {
                String fieldName = Character.toLowerCase(methodName.charAt(3))
                        + methodName.substring(4);
                beanNameToFieldName.put(methodName, fieldName);
                IMethodBinding b = getter.resolveBinding();
                if (b != null) {
                    getterKeyToFieldName.put(b.getMethodDeclaration().getKey(), fieldName);
                }
            }
            // record-style accessors (x()) already match the record name — no rename needed
        }

        // -------------------------------------------------------------------------
        // Build new source for the class file
        // -------------------------------------------------------------------------
        String recordText = buildRecordText(source, type, components, ctor, accessors);
        int typeStart = type.getStartPosition();
        int typeEnd   = typeStart + type.getLength();
        String newSource = source.substring(0, typeStart) + recordText + source.substring(typeEnd);

        // Rename bean-style getter calls in the converted file using name-based matching
        // (binding resolution cannot be used here since the getters no longer exist in newSource)
        if (!beanNameToFieldName.isEmpty()) {
            newSource = renameByName(newSource, absTarget.getFileName().toString(), beanNameToFieldName);
        }

        Map<Path, String> result = new LinkedHashMap<>();
        result.put(absTarget, newSource);

        // -------------------------------------------------------------------------
        // Rename getter call sites in other project files via binding resolution
        // -------------------------------------------------------------------------
        if (!getterKeyToFieldName.isEmpty()) {
            for (Map.Entry<Path, CompilationUnit> entry : cus.entrySet()) {
                Path filePath = entry.getKey();
                if (filePath.equals(absTarget)) continue;

                List<Object[]> edits = collectBindingRenames(
                        entry.getValue(), getterKeyToFieldName);
                if (edits.isEmpty()) continue;

                edits.sort((a, b) -> Integer.compare((int) b[0], (int) a[0]));
                StringBuilder sb = new StringBuilder(sources.get(filePath));
                for (Object[] ed : edits) sb.replace((int) ed[0], (int) ed[1], (String) ed[2]);
                result.put(filePath, sb.toString());
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private static void validatePreconditions(String source, TypeDeclaration type) {
        if (type.isInterface()) {
            throw new IllegalArgumentException("Cannot convert an interface to a record.");
        }
        String name = type.getName().getIdentifier();
        for (Object mod : type.modifiers()) {
            if (mod instanceof Modifier m && m.isAbstract()) {
                throw new IllegalArgumentException(
                        "Cannot convert abstract class '" + name + "' to a record.");
            }
        }
        if (type.getSuperclassType() != null) {
            throw new IllegalArgumentException(
                    "Cannot convert class '" + name + "' to a record: it extends '"
                    + type.getSuperclassType() + "'. Records cannot extend classes.");
        }
        // Detect if already a record by checking for 'record' keyword before first '('
        int typeStart = type.getStartPosition();
        String prefix = source.substring(typeStart, Math.min(typeStart + 200, source.length()));
        int parenIdx  = prefix.indexOf('(');
        int recordIdx = prefix.indexOf("record");
        if (recordIdx >= 0 && (parenIdx < 0 || recordIdx < parenIdx)) {
            throw new IllegalArgumentException("'" + name + "' is already a record.");
        }
    }

    // -------------------------------------------------------------------------
    // Field / component collection
    // -------------------------------------------------------------------------

    private record FieldComponent(String typeSrc, String name, FieldDeclaration declaration) {}

    @SuppressWarnings("unchecked")
    private static List<FieldComponent> collectComponents(String source, TypeDeclaration type) {
        List<FieldComponent> result = new ArrayList<>();
        for (Object bd : type.bodyDeclarations()) {
            if (!(bd instanceof FieldDeclaration fd)) continue;
            boolean isPrivate = false, isFinal = false;
            for (Object mod : fd.modifiers()) {
                if (mod instanceof Modifier m) {
                    if (m.isPrivate()) isPrivate = true;
                    if (m.isFinal())   isFinal   = true;
                }
            }
            if (!isPrivate || !isFinal) continue;
            String typeSrc = source.substring(
                    fd.getType().getStartPosition(),
                    fd.getType().getStartPosition() + fd.getType().getLength());
            for (Object frag : fd.fragments()) {
                if (frag instanceof VariableDeclarationFragment vdf) {
                    result.add(new FieldComponent(typeSrc, vdf.getName().getIdentifier(), fd));
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "Class '" + type.getName().getIdentifier()
                    + "' has no private final fields — nothing to turn into record components.");
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Constructor detection
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static MethodDeclaration findAllArgsConstructor(
            String source, TypeDeclaration type, List<FieldComponent> components) {

        Set<String> fieldNames = components.stream().map(c -> c.name).collect(Collectors.toSet());
        int fieldCount = components.size();

        for (Object bd : type.bodyDeclarations()) {
            if (!(bd instanceof MethodDeclaration md) || !md.isConstructor()) continue;
            if (md.parameters().size() != fieldCount) continue;
            if (md.getBody() == null) continue;
            if (collectAssignedFields(md.getBody()).containsAll(fieldNames)) return md;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> collectAssignedFields(Block body) {
        Set<String> assigned = new LinkedHashSet<>();
        for (Object stmt : body.statements()) {
            if (!(stmt instanceof ExpressionStatement es)) continue;
            if (!(es.getExpression() instanceof Assignment a)) continue;
            Expression lhs = a.getLeftHandSide();
            String fieldName = null;
            if (lhs instanceof FieldAccess fa && fa.getExpression() instanceof ThisExpression) {
                fieldName = fa.getName().getIdentifier();
            } else if (lhs instanceof SimpleName sn) {
                fieldName = sn.getIdentifier();
            }
            if (fieldName != null) assigned.add(fieldName);
        }
        return assigned;
    }

    // -------------------------------------------------------------------------
    // Simple accessor detection
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Set<MethodDeclaration> collectSimpleAccessors(
            String source, TypeDeclaration type, Set<String> fieldNames) {

        Set<MethodDeclaration> result = new LinkedHashSet<>();
        for (Object bd : type.bodyDeclarations()) {
            if (!(bd instanceof MethodDeclaration md)) continue;
            if (md.isConstructor() || !md.parameters().isEmpty()) continue;
            if (md.getBody() == null) continue;
            List<Statement> stmts = md.getBody().statements();
            if (stmts.size() != 1 || !(stmts.get(0) instanceof ReturnStatement rs)) continue;

            Expression ret = rs.getExpression();
            String returnedName = null;
            if (ret instanceof SimpleName sn) {
                returnedName = sn.getIdentifier();
            } else if (ret instanceof FieldAccess fa && fa.getExpression() instanceof ThisExpression) {
                returnedName = fa.getName().getIdentifier();
            }
            if (returnedName == null || !fieldNames.contains(returnedName)) continue;

            String methodName = md.getName().getIdentifier();
            boolean isBeanGetter = methodName.startsWith("get")
                    && methodName.length() > 3
                    && Character.isUpperCase(methodName.charAt(3))
                    && methodName.substring(3).equalsIgnoreCase(
                            Character.toUpperCase(returnedName.charAt(0)) + returnedName.substring(1));
            boolean isRecordAccessor = methodName.equals(returnedName);

            if (isBeanGetter || isRecordAccessor) result.add(md);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Record text generation
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static String buildRecordText(
            String source, TypeDeclaration type,
            List<FieldComponent> components,
            MethodDeclaration ctor,
            Set<MethodDeclaration> accessors) {

        StringBuilder sb = new StringBuilder();

        // Annotations and non-final modifiers
        for (Object mod : type.modifiers()) {
            if (mod instanceof Annotation ann) {
                sb.append(source, ann.getStartPosition(), ann.getStartPosition() + ann.getLength());
                sb.append("\n");
            } else if (mod instanceof Modifier m && !m.isFinal()) {
                sb.append(m.getKeyword().toString()).append(" ");
            }
        }

        // Generic type parameters
        if (!type.typeParameters().isEmpty()) {
            List<TypeParameter> tps = type.typeParameters();
            sb.append("<").append(tps.stream()
                    .map(tp -> source.substring(tp.getStartPosition(),
                            tp.getStartPosition() + tp.getLength()))
                    .collect(Collectors.joining(", "))).append(">");
        }

        sb.append("record ").append(type.getName().getIdentifier());

        if (!type.typeParameters().isEmpty()) {
            // type params already appended above — do nothing here
        }

        sb.append("(");
        sb.append(components.stream()
                .map(c -> c.typeSrc + " " + c.name)
                .collect(Collectors.joining(", ")));
        sb.append(")");

        // implements clauses
        List<Type> ifaces = type.superInterfaceTypes();
        if (!ifaces.isEmpty()) {
            sb.append(" implements ");
            sb.append(ifaces.stream()
                    .map(t -> source.substring(t.getStartPosition(),
                            t.getStartPosition() + t.getLength()))
                    .collect(Collectors.joining(", ")));
        }

        sb.append(" {");

        Set<BodyDeclaration> toRemove = new LinkedHashSet<>();
        for (FieldComponent fc : components) toRemove.add(fc.declaration);
        toRemove.add(ctor);
        toRemove.addAll(accessors);

        boolean hadContent = false;
        for (Object bd : type.bodyDeclarations()) {
            if (!(bd instanceof BodyDeclaration decl) || toRemove.contains(decl)) continue;
            sb.append("\n\n    ");
            String raw = source.substring(decl.getStartPosition(),
                    decl.getStartPosition() + decl.getLength());
            sb.append(raw.stripLeading());
            hadContent = true;
        }

        if (hadContent) sb.append("\n");
        sb.append("}");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Getter call-site renaming helpers
    // -------------------------------------------------------------------------

    /** Name-based rename: replaces every MethodInvocation whose name is in the map. */
    private static String renameByName(String source, String unitName,
            Map<String, String> nameToNewName) {
        CompilationUnit cu = parseSimple(source, unitName);
        List<int[]> edits = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation call) {
                String newName = nameToNewName.get(call.getName().getIdentifier());
                if (newName == null) return true;
                SimpleName n = call.getName();
                edits.add(new int[]{n.getStartPosition(), n.getStartPosition() + n.getLength()});
                return true;
            }
        });
        if (edits.isEmpty()) return source;
        edits.sort((a, b) -> b[0] - a[0]);
        StringBuilder sb = new StringBuilder(source);
        for (int[] e : edits) {
            String oldName = source.substring(e[0], e[1]);
            sb.replace(e[0], e[1], nameToNewName.get(oldName));
        }
        return sb.toString();
    }

    /** Binding-based rename: collects edits from a CU for matching method binding keys. */
    private static List<Object[]> collectBindingRenames(
            CompilationUnit cu, Map<String, String> keyToNewName) {
        List<Object[]> edits = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation call) {
                IMethodBinding b = call.resolveMethodBinding();
                if (b == null) return true;
                String newName = keyToNewName.get(b.getMethodDeclaration().getKey());
                if (newName == null) return true;
                SimpleName n = call.getName();
                edits.add(new Object[]{n.getStartPosition(), n.getStartPosition() + n.getLength(), newName});
                return true;
            }
        });
        return edits;
    }

    // -------------------------------------------------------------------------
    // Infrastructure
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

    private static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        for (Object o : cu.types()) {
            if (o instanceof TypeDeclaration td) return td;
        }
        throw new IllegalArgumentException("No class declaration found in source.");
    }

    private static CompilationUnit parseSimple(String source, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(false);
        return (CompilationUnit) parser.createAST(null);
    }
}
