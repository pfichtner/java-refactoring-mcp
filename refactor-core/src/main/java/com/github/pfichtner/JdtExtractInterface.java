package com.github.pfichtner;

import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Headless Extract Interface refactoring using JDT ASTParser.
 *
 * <p>Generates a new Java interface from the public non-static methods of a class
 * and adds {@code implements InterfaceName} to the class declaration.
 *
 * <p>Supported:
 * <ul>
 *   <li>All public non-static, non-constructor methods (when {@code methodNames} is empty)</li>
 *   <li>A specified subset of method names</li>
 *   <li>Handles existing implements clauses (appends to them)</li>
 *   <li>Preserves parameter types, throws clauses, and generic type parameters</li>
 * </ul>
 *
 * <p>Precondition failures:
 * <ul>
 *   <li>No public non-static methods found (nothing to extract)</li>
 *   <li>Named method not found in the class</li>
 * </ul>
 */
public class JdtExtractInterface {

    /**
     * Result of an extract-interface operation.
     *
     * @param modifiedClassSource the class source with {@code implements InterfaceName} added
     * @param interfaceSource     the full source of the new interface file
     */
    public record Result(String modifiedClassSource, String interfaceSource) {}

    /**
     * Extracts an interface from the given class source.
     *
     * @param classSource   full source text of the class file
     * @param unitName      file name for binding resolution (e.g. {@code "Calculator.java"})
     * @param interfaceName simple name for the new interface
     * @param methodNames   method names to include; empty list = all public non-static methods
     * @return {@link Result} containing the modified class and the new interface source
     */
    public static Result extractInterface(
            String classSource, String unitName,
            String interfaceName, List<String> methodNames) {

        CompilationUnit cu = parse(classSource, unitName);

        TypeDeclaration typeDecl = findPrimaryType(cu);
        if (typeDecl.isInterface()) {
            throw new IllegalArgumentException(
                    "Cannot extract an interface from another interface.");
        }

        @SuppressWarnings("unchecked")
        List<MethodDeclaration> allMethods = ((List<Object>) typeDecl.bodyDeclarations()).stream()
                .filter(o -> o instanceof MethodDeclaration md
                        && isPublicNonStatic(md)
                        && !md.isConstructor())
                .map(o -> (MethodDeclaration) o)
                .collect(Collectors.toList());

        if (allMethods.isEmpty()) {
            throw new IllegalArgumentException(
                    "No public non-static methods found — nothing to extract.");
        }

        // Filter to requested method names (if any)
        List<MethodDeclaration> selected;
        if (methodNames.isEmpty()) {
            selected = allMethods;
        } else {
            Set<String> wanted = Set.copyOf(methodNames);
            selected = allMethods.stream()
                    .filter(m -> wanted.contains(m.getName().getIdentifier()))
                    .collect(Collectors.toList());
            Set<String> found = selected.stream()
                    .map(m -> m.getName().getIdentifier())
                    .collect(Collectors.toSet());
            Set<String> missing = wanted.stream()
                    .filter(n -> !found.contains(n))
                    .collect(Collectors.toSet());
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException(
                        "Methods not found in class: " + missing);
            }
        }

        // -------------------------------------------------------------------------
        // Generate interface source
        // -------------------------------------------------------------------------
        String pkg = cu.getPackage() != null
                ? "package " + cu.getPackage().getName().getFullyQualifiedName() + ";\n\n"
                : "";

        StringBuilder iface = new StringBuilder();
        iface.append(pkg);
        iface.append("public interface ").append(interfaceName).append(" {\n");
        iface.append(selected.stream()
                .map(m -> "    " + buildSignature(classSource, m) + ";\n")
                .collect(Collectors.joining()));
        iface.append("}\n");

        // -------------------------------------------------------------------------
        // Modify class: add implements InterfaceName
        // -------------------------------------------------------------------------
        @SuppressWarnings("unchecked")
        List<Type> existingInterfaces = typeDecl.superInterfaceTypes();

        StringBuilder sb = new StringBuilder(classSource);
        if (existingInterfaces.isEmpty()) {
            // Find the class body opening '{' safely from AST end positions, scanning
            // from after the class name (and optional type parameters) to avoid false
            // hits from '{' inside annotations on the class declaration.
            @SuppressWarnings("unchecked")
            List<TypeParameter> typeParams = typeDecl.typeParameters();
            int searchFrom = typeDecl.getName().getStartPosition() + typeDecl.getName().getLength();
            if (!typeParams.isEmpty()) {
                TypeParameter last = typeParams.get(typeParams.size() - 1);
                searchFrom = last.getStartPosition() + last.getLength();
            }
            int bracePos = classSource.indexOf('{', searchFrom);
            // Replace whitespace immediately before '{' with " implements Name "
            int wsStart = bracePos;
            while (wsStart > 0 && classSource.charAt(wsStart - 1) == ' ') wsStart--;
            sb.replace(wsStart, bracePos, " implements " + interfaceName + " ");
        } else {
            Type last = existingInterfaces.get(existingInterfaces.size() - 1);
            int insertAt = last.getStartPosition() + last.getLength();
            sb.insert(insertAt, ", " + interfaceName);
        }

        return new Result(sb.toString(), iface.toString());
    }

    // -------------------------------------------------------------------------
    // Method signature builder
    // -------------------------------------------------------------------------

    private static String buildSignature(String source, MethodDeclaration m) {
        StringBuilder sig = new StringBuilder();

        // Generic type parameters
        @SuppressWarnings("unchecked") List<TypeParameter> typeParams = m.typeParameters();
        if (!typeParams.isEmpty()) {
            sig.append("<");
            sig.append(typeParams.stream()
                    .map(tp -> source.substring(tp.getStartPosition(), tp.getStartPosition() + tp.getLength()))
                    .collect(Collectors.joining(", ")));
            sig.append("> ");
        }

        // Return type
        Type rt = m.getReturnType2();
        if (rt != null) {
            sig.append(source, rt.getStartPosition(), rt.getStartPosition() + rt.getLength());
            sig.append(" ");
        }

        // Method name
        sig.append(m.getName().getIdentifier());

        // Parameters
        sig.append("(");
        @SuppressWarnings("unchecked") List<SingleVariableDeclaration> params = m.parameters();
        sig.append(params.stream()
                .map(p -> source.substring(p.getStartPosition(), p.getStartPosition() + p.getLength()))
                .collect(Collectors.joining(", ")));
        sig.append(")");

        // Throws
        @SuppressWarnings("unchecked") List<Type> thrown = m.thrownExceptionTypes();
        if (!thrown.isEmpty()) {
            sig.append(" throws ");
            sig.append(thrown.stream()
                    .map(t -> source.substring(t.getStartPosition(), t.getStartPosition() + t.getLength()))
                    .collect(Collectors.joining(", ")));
        }

        return sig.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isPublicNonStatic(MethodDeclaration m) {
        return m.modifiers().stream().noneMatch(o -> o instanceof Modifier mod && mod.isStatic())
                && m.modifiers().stream().anyMatch(o -> o instanceof Modifier mod && mod.isPublic());
    }

    private static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        @SuppressWarnings("unchecked")
        List<Object> types = cu.types();
        return types.stream()
                .filter(o -> o instanceof TypeDeclaration)
                .map(o -> (TypeDeclaration) o)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No type declaration found in the source."));
    }

    private static CompilationUnit parse(String source, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        return (CompilationUnit) parser.createAST(null);
    }
}
