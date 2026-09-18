package dev.mcp.refactor;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless Inline Variable refactoring using JDT ASTParser.
 *
 * <p>Replaces every use of a local variable with its initializer expression,
 * then removes the declaration statement.
 *
 * <p>Parenthesises the inlined expression when the operator context requires it
 * (e.g. inlining {@code a + b} into {@code x * sum} → {@code x * (a + b)}).
 *
 * <p>Precondition failures (structured diagnostic, no file modified):
 * <ul>
 *   <li>Target is not a local variable (fields, parameters rejected)</li>
 *   <li>Declaration has multiple fragments ({@code int x = 1, y = 2})</li>
 *   <li>Variable has no initializer ({@code int x; x = 5;})</li>
 * </ul>
 */
public class JdtInliner {

    /**
     * Inlines the local variable at {@code offset} and returns the rewritten source.
     *
     * @param source   full source text
     * @param unitName file name for binding resolution (e.g. {@code "Foo.java"})
     * @param offset   character offset of any character within the variable name
     * @return rewritten source with all uses replaced and declaration removed
     * @throws IllegalArgumentException if the inline cannot be performed safely
     */
    public static String inlineVariable(String source, String unitName, int offset) {
        CompilationUnit cu = parse(source, unitName);

        SimpleName target = findSimpleName(cu, offset);
        IBinding binding = target.resolveBinding();

        if (!(binding instanceof IVariableBinding vb)) {
            throw new IllegalArgumentException(
                    "No variable found at offset " + offset + ".");
        }
        if (vb.isField() || vb.isEnumConstant()) {
            throw new IllegalArgumentException(
                    "Cannot inline field '" + vb.getName() + "'. Only local variables are supported.");
        }
        if (vb.isParameter()) {
            throw new IllegalArgumentException(
                    "Cannot inline parameter '" + vb.getName() + "'.");
        }

        String bindingKey = vb.getKey();

        // Find the declaration
        VariableDeclarationStatement decl = findDeclaration(cu, bindingKey);
        if (decl == null) {
            throw new IllegalArgumentException(
                    "Cannot find the declaration of '" + vb.getName() + "' in this compilation unit.");
        }
        if (decl.fragments().size() > 1) {
            throw new IllegalArgumentException(
                    "Cannot inline '" + vb.getName()
                    + "': declaration contains multiple variables. Split it first.");
        }

        VariableDeclarationFragment fragment = (VariableDeclarationFragment) decl.fragments().get(0);
        Expression init = fragment.getInitializer();
        if (init == null) {
            throw new IllegalArgumentException(
                    "Cannot inline '" + vb.getName() + "': variable has no initializer.");
        }

        // Extract initializer text, parenthesising if necessary
        String initText = source.substring(
                init.getStartPosition(), init.getStartPosition() + init.getLength());
        String safeInit = needsParens(init) ? "(" + initText + ")" : initText;

        // Collect all uses (every SimpleName with the same binding key, except the declaration name)
        List<SimpleName> uses = collectUses(cu, bindingKey, fragment.getName());

        // Find declaration line boundaries (in the original source)
        int declStart = decl.getStartPosition();
        int lineStart = declStart;
        while (lineStart > 0 && source.charAt(lineStart - 1) != '\n') lineStart--;
        int lineEnd = declStart + decl.getLength();
        while (lineEnd < source.length() && source.charAt(lineEnd) != '\n') lineEnd++;
        if (lineEnd < source.length()) lineEnd++; // include the newline

        // Sort uses largest-to-smallest so each replacement doesn't shift earlier positions
        uses.sort((a, b) -> b.getStartPosition() - a.getStartPosition());

        // Apply use replacements from end to start
        StringBuilder sb = new StringBuilder(source);
        for (SimpleName use : uses) {
            sb.replace(use.getStartPosition(), use.getStartPosition() + use.getLength(), safeInit);
        }

        // Remove the declaration line (positions still valid: declaration precedes all uses)
        sb.delete(lineStart, lineEnd);

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<SimpleName> collectUses(
            CompilationUnit cu, String bindingKey, SimpleName declarationName) {
        List<SimpleName> result = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                if (node == declarationName) return true; // skip the declaration itself
                IBinding b = node.resolveBinding();
                if (b != null && bindingKey.equals(b.getKey())) {
                    result.add(node);
                }
                return true;
            }
        });
        return result;
    }

    private static VariableDeclarationStatement findDeclaration(
            CompilationUnit cu, String bindingKey) {
        VariableDeclarationStatement[] found = {null};
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                for (Object o : node.fragments()) {
                    VariableDeclarationFragment f = (VariableDeclarationFragment) o;
                    IVariableBinding b = f.resolveBinding();
                    if (b != null && bindingKey.equals(b.getKey())) {
                        found[0] = node;
                        return false;
                    }
                }
                return found[0] == null;
            }
        });
        return found[0];
    }

    /**
     * Returns {@code true} when the expression needs to be wrapped in parentheses
     * to preserve semantics when inlined into an unknown operator context.
     * Conservative: parenthesises any compound expression.
     */
    private static boolean needsParens(Expression expr) {
        return !(expr instanceof SimpleName
                || expr instanceof QualifiedName
                || expr instanceof NumberLiteral
                || expr instanceof StringLiteral
                || expr instanceof BooleanLiteral
                || expr instanceof CharacterLiteral
                || expr instanceof NullLiteral
                || expr instanceof MethodInvocation
                || expr instanceof ClassInstanceCreation
                || expr instanceof ArrayAccess
                || expr instanceof FieldAccess
                || expr instanceof SuperFieldAccess
                || expr instanceof SuperMethodInvocation
                || expr instanceof ThisExpression
                || expr instanceof ParenthesizedExpression);
    }

    private static SimpleName findSimpleName(CompilationUnit cu, int offset) {
        ASTNode node = NodeFinder.perform(cu, offset, 1);
        if (node == null) throw new IllegalArgumentException("No AST node at offset " + offset);
        while (node != null && !(node instanceof SimpleName)) node = node.getParent();
        if (!(node instanceof SimpleName sn)) {
            throw new IllegalArgumentException("No name found at offset " + offset);
        }
        return sn;
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
