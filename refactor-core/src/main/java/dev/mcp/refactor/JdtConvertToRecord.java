package dev.mcp.refactor;

import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Headless Convert-Class-to-Record refactoring using JDT ASTParser.
 *
 * <p>Converts a simple data class into a Java record declaration (Java 16+):
 * <ol>
 *   <li>The {@code private final} fields become record components.</li>
 *   <li>The all-args constructor is removed (records auto-generate it).</li>
 *   <li>Simple accessor methods (those that just {@code return fieldName;}) are removed
 *       (the record auto-generates {@code fieldName()} accessors).</li>
 *   <li>All other methods (custom logic, {@code toString}, {@code equals}, …) are
 *       retained in the record body.</li>
 *   <li>Any {@code implements} clauses are preserved.</li>
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
 *       only; other naming conventions are left in the record body.</li>
 *   <li>Generic type parameters on the class are preserved verbatim.</li>
 * </ul>
 */
public class JdtConvertToRecord {

    /**
     * Converts the primary class in {@code source} to a record.
     *
     * @param source   full source of the Java file
     * @param unitName file name used for parsing (e.g. {@code "Point.java"})
     * @return the rewritten source containing the record declaration
     * @throws IllegalArgumentException if the class does not satisfy the preconditions
     */
    public static String convertToRecord(String source, String unitName) {
        CompilationUnit cu = parse(source, unitName);
        TypeDeclaration type = findPrimaryType(cu);

        validatePreconditions(source, type);

        // Collect private final fields (the future record components), in declaration order
        List<FieldComponent> components = collectComponents(source, type);

        // Find the all-args constructor
        MethodDeclaration ctor = findAllArgsConstructor(source, type, components);
        if (ctor == null) {
            throw new IllegalArgumentException(
                    "Class '" + type.getName().getIdentifier()
                    + "' has no all-args constructor that assigns every private final field. "
                    + "Expected a constructor with " + components.size() + " parameter(s) "
                    + "setting: " + components.stream().map(c -> c.name).collect(Collectors.joining(", ")) + ".");
        }

        // Collect simple accessor methods to drop
        Set<String> fieldNames = components.stream().map(c -> c.name).collect(Collectors.toSet());
        Set<MethodDeclaration> accessors = collectSimpleAccessors(source, type, fieldNames);

        // Build the record text and replace the TypeDeclaration in the original source
        String recordText = buildRecordText(source, type, components, ctor, accessors);
        int start = type.getStartPosition();
        int end   = start + type.getLength();
        return source.substring(0, start) + recordText + source.substring(end);
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
            if (mod instanceof Modifier m) {
                if (m.isAbstract()) {
                    throw new IllegalArgumentException(
                            "Cannot convert abstract class '" + name + "' to a record.");
                }
            }
        }

        if (type.getSuperclassType() != null) {
            throw new IllegalArgumentException(
                    "Cannot convert class '" + name + "' to a record: it extends '"
                    + type.getSuperclassType() + "'. Records cannot extend classes.");
        }

        // Check it isn't already a record (JDT TypeDeclaration for records has isRecord() in newer APIs,
        // but we can detect it by looking for the 'record' keyword in source near the type start)
        int typeStart = type.getStartPosition();
        String prefix = source.substring(typeStart, Math.min(typeStart + 200, source.length()));
        if (prefix.matches("(?s).*?\\brecord\\b.*?\\(.*")) {
            // Heuristic: if 'record' keyword appears before the opening '('
            int parenIdx  = prefix.indexOf('(');
            int recordIdx = prefix.indexOf("record");
            if (recordIdx >= 0 && (parenIdx < 0 || recordIdx < parenIdx)) {
                throw new IllegalArgumentException(
                        "'" + name + "' is already a record.");
            }
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
            List<SingleVariableDeclaration> params = md.parameters();
            if (params.size() != fieldCount) continue;
            if (md.getBody() == null) continue;

            // Verify the body assigns every field
            Set<String> assigned = collectAssignedFields(md.getBody());
            if (assigned.containsAll(fieldNames)) return md;
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
            if (md.isConstructor()) continue;
            if (!md.parameters().isEmpty()) continue;
            if (md.getBody() == null) continue;
            List<Statement> stmts = md.getBody().statements();
            if (stmts.size() != 1) continue;
            if (!(stmts.get(0) instanceof ReturnStatement rs)) continue;

            // Return expression must be a simple field reference
            Expression ret = rs.getExpression();
            String returnedName = null;
            if (ret instanceof SimpleName sn) {
                returnedName = sn.getIdentifier();
            } else if (ret instanceof FieldAccess fa && fa.getExpression() instanceof ThisExpression) {
                returnedName = fa.getName().getIdentifier();
            }
            if (returnedName == null || !fieldNames.contains(returnedName)) continue;

            // Method name must be getXxx() or xxx() (matching the field name)
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

        // Type parameters (generics on class, e.g. class Foo<T>)
        String typeParams = "";
        if (!type.typeParameters().isEmpty()) {
            List<TypeParameter> tps = type.typeParameters();
            typeParams = "<" + tps.stream()
                    .map(tp -> source.substring(tp.getStartPosition(), tp.getStartPosition() + tp.getLength()))
                    .collect(Collectors.joining(", ")) + ">";
        }

        sb.append("record ").append(type.getName().getIdentifier()).append(typeParams).append("(");
        sb.append(components.stream()
                .map(c -> c.typeSrc + " " + c.name)
                .collect(Collectors.joining(", ")));
        sb.append(")");

        // implements clauses
        List<Type> ifaces = type.superInterfaceTypes();
        if (!ifaces.isEmpty()) {
            sb.append(" implements ");
            sb.append(ifaces.stream()
                    .map(t -> source.substring(t.getStartPosition(), t.getStartPosition() + t.getLength()))
                    .collect(Collectors.joining(", ")));
        }

        sb.append(" {");

        // Body: keep everything except fields, ctor, and simple accessors
        Set<BodyDeclaration> toRemove = new LinkedHashSet<>();
        for (FieldComponent fc : components) toRemove.add(fc.declaration);
        toRemove.add(ctor);
        toRemove.addAll(accessors);

        boolean hadContent = false;
        for (Object bd : type.bodyDeclarations()) {
            if (!(bd instanceof BodyDeclaration decl)) continue;
            if (toRemove.contains(decl)) continue;
            sb.append("\n\n    ");
            // Re-indent: strip the existing leading indent from first line
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
    // Infrastructure
    // -------------------------------------------------------------------------

    private static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        for (Object o : cu.types()) {
            if (o instanceof TypeDeclaration td) return td;
        }
        throw new IllegalArgumentException("No class declaration found in source.");
    }

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
