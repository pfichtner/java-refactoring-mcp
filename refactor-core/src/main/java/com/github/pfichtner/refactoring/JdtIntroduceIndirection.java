package com.github.pfichtner.refactoring;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Headless Introduce-Indirection refactoring using JDT ASTParser.
 *
 * <p>Adds a new {@code public static} method that delegates to an existing method,
 * creating a layer of indirection. For non-static methods the new static method
 * takes the receiver object as its first parameter:
 *
 * <pre>{@code
 * // Before — Service.java
 * public class Service {
 *     public String process(String input) { return input.trim(); }
 * }
 *
 * // After: introduceIndirection(project, serviceFile, offset, "doProcess")
 * public class Service {
 *     public String process(String input) { return input.trim(); }
 *
 *     public static String doProcess(Service service, String input) {
 *         return service.process(input);
 *     }
 * }
 * }</pre>
 *
 * <p>For {@code static} methods the receiver parameter is omitted and the delegate
 * calls the original directly:
 *
 * <pre>{@code
 * public static int doSquare(int x) { return square(x); }
 * }</pre>
 *
 * <p>Precondition failures ({@link IllegalArgumentException}):
 * <ul>
 *   <li>No method declaration at the given offset</li>
 *   <li>The enclosing class already has a method with {@code indirectionMethodName}</li>
 * </ul>
 *
 * <p>Limitations:
 * <ul>
 *   <li>Only single-file: the new method is added to the class that declares the original.</li>
 *   <li>Call sites are not updated — the new indirection method is added alongside the original.</li>
 *   <li>Generic type parameters on the original method are not transferred to the new method.</li>
 *   <li>Throws clauses are not transferred to the indirection method.</li>
 * </ul>
 */
public class JdtIntroduceIndirection {

    /**
     * Introduces a static indirection method next to the method at {@code offset}.
     *
     * @param source                full source text
     * @param unitName              file name for error messages (e.g. {@code "Service.java"})
     * @param offset                character offset inside the method to wrap
     * @param indirectionMethodName simple name for the new static wrapper method
     * @return rewritten source
     * @throws IllegalArgumentException if preconditions are not met
     */
    public static String introduceIndirection(
            String source, String unitName, int offset, String indirectionMethodName) {

        CompilationUnit cu = parse(source, unitName);

        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) {
            throw new IllegalArgumentException("No AST node at offset " + offset + ".");
        }

        MethodDeclaration method = findMethodDeclaration(node);
        if (method == null) {
            throw new IllegalArgumentException(
                    "No method declaration found at offset " + offset + ". "
                    + "Place cursor inside a method signature or body.");
        }

        String originalMethodName = method.getName().getIdentifier();
        TypeDeclaration enclosingType = findEnclosingType(method);
        if (enclosingType == null) {
            throw new IllegalArgumentException(
                    "Method '" + originalMethodName + "' is not inside a class declaration.");
        }

        // Check for name collision
        for (Object bd : enclosingType.bodyDeclarations()) {
            if (bd instanceof MethodDeclaration md
                    && md.getName().getIdentifier().equals(indirectionMethodName)) {
                throw new IllegalArgumentException(
                        "Method '" + indirectionMethodName + "()' already exists in '"
                        + enclosingType.getName().getIdentifier() + "'.");
            }
        }

        boolean isStatic = isStatic(method.modifiers());
        String returnTypeName = method.getReturnType2().toString();
        String className = enclosingType.getName().getIdentifier();

        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> params = method.parameters();

        // Build the indirection method text
        String indent = detectIndent(source, enclosingType);
        String newMethod = buildIndirectionMethod(
                indirectionMethodName, originalMethodName,
                returnTypeName, className, params, source, isStatic, indent);

        // Insert before the closing brace of the enclosing type
        int typeEnd = enclosingType.getStartPosition() + enclosingType.getLength();
        int insertAt = source.lastIndexOf('}', typeEnd - 1);

        StringBuilder sb = new StringBuilder(source);
        sb.insert(insertAt, "\n\n" + newMethod + "\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Code generation
    // -------------------------------------------------------------------------

    private static String buildIndirectionMethod(
            String newName, String originalName,
            String returnType, String className,
            List<SingleVariableDeclaration> originalParams,
            String source, boolean isStatic, String indent) {

        List<String> paramDecls   = new ArrayList<>();
        List<String> argNames     = new ArrayList<>();

        if (!isStatic) {
            // Add the receiver as the first parameter
            paramDecls.add(className + " " + lowerFirst(className));
        }

        for (SingleVariableDeclaration p : originalParams) {
            String pType = p.getType().toString();
            if (p.isVarargs()) pType += "...";
            String pName = p.getName().getIdentifier();
            paramDecls.add(pType + " " + pName);
            argNames.add(pName);
        }

        String paramList = String.join(", ", paramDecls);
        String argList   = String.join(", ", argNames);

        boolean returnsVoid = "void".equals(returnType);

        // Build the delegate call
        String receiverCall = isStatic
                ? originalName + "(" + argList + ")"
                : lowerFirst(className) + "." + originalName + "(" + argList + ")";

        String body = returnsVoid
                ? receiverCall + ";"
                : "return " + receiverCall + ";";

        String bodyIndent = indent + "    ";
        return indent + "public static " + returnType + " " + newName + "(" + paramList + ") {\n"
                + bodyIndent + body + "\n"
                + indent + "}";
    }

    private static String lowerFirst(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    // -------------------------------------------------------------------------
    // AST navigation
    // -------------------------------------------------------------------------

    private static MethodDeclaration findMethodDeclaration(ASTNode node) {
        ASTNode current = node;
        while (current != null) {
            if (current instanceof MethodDeclaration md) return md;
            current = current.getParent();
        }
        return null;
    }

    private static TypeDeclaration findEnclosingType(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof TypeDeclaration td) return td;
            current = current.getParent();
        }
        return null;
    }

    private static boolean isStatic(List<?> modifiers) {
        for (Object mod : modifiers) {
            if (mod instanceof Modifier m && m.getKeyword() == Modifier.ModifierKeyword.STATIC_KEYWORD) {
                return true;
            }
        }
        return false;
    }

    private static String detectIndent(String source, TypeDeclaration type) {
        for (Object bd : type.bodyDeclarations()) {
            if (bd instanceof ASTNode n) {
                int start     = n.getStartPosition();
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
