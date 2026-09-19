package com.github.pfichtner;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
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
        List<MethodDeclaration> allMethods = new ArrayList<>();
        for (Object o : typeDecl.bodyDeclarations()) {
            if (o instanceof MethodDeclaration md
                    && isPublicNonStatic(md)
                    && !md.isConstructor()) {
                allMethods.add(md);
            }
        }

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
        for (MethodDeclaration m : selected) {
            iface.append("    ").append(buildSignature(classSource, m)).append(";\n");
        }
        iface.append("}\n");

        // -------------------------------------------------------------------------
        // Modify class: add implements InterfaceName
        // -------------------------------------------------------------------------
        int bracePos = classSource.indexOf('{', typeDecl.getStartPosition());

        @SuppressWarnings("unchecked")
        List<Type> existingInterfaces = typeDecl.superInterfaceTypes();

        StringBuilder sb = new StringBuilder(classSource);
        if (existingInterfaces.isEmpty()) {
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
            for (int i = 0; i < typeParams.size(); i++) {
                if (i > 0) sig.append(", ");
                TypeParameter tp = typeParams.get(i);
                sig.append(source, tp.getStartPosition(), tp.getStartPosition() + tp.getLength());
            }
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
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sig.append(", ");
            SingleVariableDeclaration p = params.get(i);
            sig.append(source, p.getStartPosition(), p.getStartPosition() + p.getLength());
        }
        sig.append(")");

        // Throws
        @SuppressWarnings("unchecked") List<Type> thrown = m.thrownExceptionTypes();
        if (!thrown.isEmpty()) {
            sig.append(" throws ");
            for (int i = 0; i < thrown.size(); i++) {
                if (i > 0) sig.append(", ");
                Type t = thrown.get(i);
                sig.append(source, t.getStartPosition(), t.getStartPosition() + t.getLength());
            }
        }

        return sig.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isPublicNonStatic(MethodDeclaration m) {
        for (Object o : m.modifiers()) {
            if (o instanceof Modifier mod) {
                if (mod.isStatic()) return false;
            }
        }
        for (Object o : m.modifiers()) {
            if (o instanceof Modifier mod && mod.isPublic()) return true;
        }
        return false;
    }

    private static TypeDeclaration findPrimaryType(CompilationUnit cu) {
        for (Object o : cu.types()) {
            if (o instanceof TypeDeclaration td) return td;
        }
        throw new IllegalArgumentException("No type declaration found in the source.");
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
