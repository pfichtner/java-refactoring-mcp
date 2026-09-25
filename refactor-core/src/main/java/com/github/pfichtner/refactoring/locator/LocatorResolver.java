package com.github.pfichtner.refactoring.locator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import com.github.pfichtner.refactoring.JdtProjectSources;
import com.github.pfichtner.refactoring.JdtRenamer;

/**
 * Resolves a {@link Locator} to a character offset in a Java source string.
 *
 * <p>Name-based variants parse the source using JDT's ASTParser (without
 * binding resolution — no classpath needed) and walk the AST to find the
 * named element. The returned offset points to the first character of the
 * element's simple-name identifier, matching what position-based callers
 * already provide to the refactoring engine.
 */
public final class LocatorResolver {

    private LocatorResolver() {}

    /**
     * Resolves {@code locator} to a 0-based character offset within {@code source}.
     *
     * @param locator   the element locator
     * @param source    full source text of the compilation unit
     * @param unitName  simple file name used for error messages (e.g. {@code "Foo.java"})
     * @return 0-based character offset of the named element's identifier
     * @throws IllegalArgumentException if the element cannot be found or is ambiguous
     */
    public static int resolve(Locator locator, String source, String unitName) {
        return switch (locator) {
            case Locator.Position(int line, int col) ->
                    JdtRenamer.toOffset(source, line, col);
            case Locator.LineOnly lo ->
                    resolveLineOnly(lo.line(), source, unitName);
            case Locator.MethodName mn ->
                    resolveMethod(mn.nameSpec(), mn.className(), source, unitName);
            case Locator.FieldName fn ->
                    resolveField(fn.name(), fn.className(), source, unitName);
            case Locator.TypeName tn ->
                    resolveType(tn.name(), source, unitName);
            case Locator.ParameterInMethod pm ->
                    resolveParameter(pm.methodSpec(), pm.paramName(), source, unitName);
            case Locator.VariableName vn ->
                    resolveVariable(vn.name(), vn.methodSpec(), source, unitName);
            case Locator.Verified(var posLocator, var nameLocator) ->
                    resolveVerified(posLocator, nameLocator, source, unitName);
        };
    }

    private static int resolveVerified(Locator posLocator, Locator nameLocator,
                                       String source, String unitName) {
        int nameOff = resolve(nameLocator, source, unitName);
        int posOff  = resolve(posLocator,  source, unitName);

        CompilationUnit cu = JdtProjectSources.parseUnit(source, unitName);
        ASTNode nameNode = NodeFinder.perform(cu, nameOff, 1);
        int nodeStart = nameNode.getStartPosition();
        int nodeLen   = nameNode.getLength();

        if (posOff >= nodeStart && posOff < nodeStart + nodeLen)
            return nameOff;

        ASTNode posNode = NodeFinder.perform(cu, posOff, 1);
        String atPos  = (posNode instanceof SimpleName sn)
            ? " (at identifier \"" + sn.getIdentifier() + "\")" : "";
        String atName = (nameNode instanceof SimpleName sn)
            ? "\"" + sn.getIdentifier() + "\"" : "offset " + nameOff;
        throw new IllegalArgumentException(
            "Position and name locators disagree: position offset " + posOff + atPos +
            " is not within the identifier " + atName +
            " (span [" + nodeStart + ", " + (nodeStart + nodeLen) + "))." +
            " Align the position with the identifier start, or pass only the name locator.");
    }

    // -------------------------------------------------------------------------
    // Per-kind resolution
    // -------------------------------------------------------------------------

    private static int resolveMethod(String nameSpec, String className,
                                     String source, String unitName) {
        ParsedName parsed = ParsedName.parse(nameSpec);
        CompilationUnit cu = JdtProjectSources.parseUnit(source, unitName);
        List<MethodDeclaration> matches = new ArrayList<>();

        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (!node.getName().getIdentifier().equals(parsed.name())) return true;
                if (className != null && !enclosingTypeName(node).equals(className)) return true;
                if (parsed.paramTypes() != null && !paramTypesMatch(node, parsed.paramTypes())) return true;
                matches.add(node);
                return true;
            }
        });

        return nameOffset(matches, nameSpec, "method", unitName).getName().getStartPosition();
    }

    private static int resolveField(String name, String className,
                                    String source, String unitName) {
        CompilationUnit cu = JdtProjectSources.parseUnit(source, unitName);
        List<VariableDeclarationFragment> matches = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                if (!node.getName().getIdentifier().equals(name)) return true;
                if (!(node.getParent() instanceof FieldDeclaration)) return true;
                if (className != null && !enclosingTypeName(node).equals(className)) return true;
                matches.add(node);
                return true;
            }
        });
        return validateSingle(matches,
                "Field '" + name + "' not found in " + unitName,
                "Ambiguous: multiple fields named '" + name + "' in " + unitName
                + ". Specify 'class' to narrow the scope."
        ).getName().getStartPosition();
    }

    private static int resolveType(String name, String source, String unitName) {
        CompilationUnit cu = JdtProjectSources.parseUnit(source, unitName);
        List<AbstractTypeDeclaration> matches = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            private void checkType(AbstractTypeDeclaration node) {
                if (node.getName().getIdentifier().equals(name)) matches.add(node);
            }
            @Override public boolean visit(TypeDeclaration node)           { checkType(node); return true; }
            @Override public boolean visit(EnumDeclaration node)           { checkType(node); return true; }
            @Override public boolean visit(AnnotationTypeDeclaration node) { checkType(node); return true; }
        });
        return validateSingle(matches,
                "Type '" + name + "' not found in " + unitName,
                "Ambiguous: multiple types named '" + name + "' in " + unitName
        ).getName().getStartPosition();
    }

    private static int resolveParameter(String methodSpec, String paramName,
                                        String source, String unitName) {
        MethodDeclaration enclosing = findMethodBySpec(methodSpec, JdtProjectSources.parseUnit(source, unitName), unitName);
        List<SingleVariableDeclaration> matches = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<SingleVariableDeclaration> params = enclosing.parameters();
        params.stream()
                .filter(p -> p.getName().getIdentifier().equals(paramName))
                .forEach(matches::add);
        ParsedName parsed = ParsedName.parse(methodSpec);
        return validateSingle(matches,
                "Parameter '" + paramName + "' in method '" + methodSpec + "' not found in " + unitName,
                "Ambiguous: parameter '" + paramName + "' in method '" + methodSpec
                + "' matches multiple locations in " + unitName
                + ". Specify parameter types in method spec, e.g. '" + parsed.name() + "(String, int)'"
        ).getName().getStartPosition();
    }

    private static int resolveVariable(String name, String methodSpec, String source, String unitName) {
        MethodDeclaration enclosing = findMethodBySpec(methodSpec, JdtProjectSources.parseUnit(source, unitName), unitName);
        return findLocalVariable(enclosing, name, methodSpec, unitName).getName().getStartPosition();
    }

    private static MethodDeclaration findMethodBySpec(String methodSpec, CompilationUnit cu, String unitName) {
        ParsedName parsed = ParsedName.parse(methodSpec);
        List<MethodDeclaration> matches = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (!node.getName().getIdentifier().equals(parsed.name())) return true;
                if (parsed.paramTypes() != null && !paramTypesMatch(node, parsed.paramTypes())) return true;
                matches.add(node);
                return true;
            }
        });
        return nameOffset(matches, methodSpec, "method", unitName);
    }

    private static VariableDeclarationFragment findLocalVariable(
            MethodDeclaration enclosing, String name, String methodSpec, String unitName) {
        List<VariableDeclarationFragment> vars = new ArrayList<>();
        enclosing.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                if (node.getName().getIdentifier().equals(name)) vars.add(node);
                return true;
            }
        });
        return validateSingle(vars,
                "Local variable '" + name + "' not found in method '" + methodSpec + "' in " + unitName,
                "Ambiguous: multiple variables named '" + name + "' in method '" + methodSpec
                + "' in " + unitName + ". Use --line or --line/--column to disambiguate."
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String enclosingTypeName(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof AbstractTypeDeclaration td) {
                return td.getName().getIdentifier();
            }
            current = current.getParent();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static boolean paramTypesMatch(MethodDeclaration node, List<String> paramTypes) {
        List<SingleVariableDeclaration> params = node.parameters();
        if (params.size() != paramTypes.size()) return false;
        return IntStream.range(0, paramTypes.size())
                .allMatch(i -> params.get(i).getType().toString().equals(paramTypes.get(i).trim()));
    }

    private static <T> T validateSingle(List<T> matches, String emptyMsg, String ambiguousMsg) {
        if (matches.isEmpty()) throw new IllegalArgumentException(emptyMsg);
        if (matches.size() > 1) throw new IllegalArgumentException(ambiguousMsg);
        return matches.get(0);
    }

    private static MethodDeclaration nameOffset(
            List<MethodDeclaration> matches, String nameSpec, String kind, String unitName) {
        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    kind.substring(0, 1).toUpperCase() + kind.substring(1) +
                    " '" + nameSpec + "' not found in " + unitName);
        }
        if (matches.size() > 1) {
            String base = nameSpec.contains("(") ? nameSpec : nameSpec.trim();
            throw new IllegalArgumentException(
                    "Ambiguous: " + kind + " '" + base + "' has multiple overloads in " + unitName +
                    ". Specify parameter types, e.g. '" + base + "(int, int)'");
        }
        return matches.get(0);
    }

    private static int resolveLineOnly(int line, String source, String unitName) {
        CompilationUnit cu = JdtProjectSources.parseUnit(source, unitName);
        List<SimpleName> matches = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                if (isDeclarationName(node) && cu.getLineNumber(node.getStartPosition()) == line)
                    matches.add(node);
                return true;
            }
        });
        return validateSingle(matches,
                "No named element at line " + line + " in " + unitName
                + ". Use --variable, --method, or another name-based locator.",
                "Ambiguous: " + matches.size() + " elements at line " + line
                + " in " + unitName + ". Specify --column or a name-based locator."
        ).getStartPosition();
    }

    private static boolean isDeclarationName(SimpleName name) {
        ASTNode p = name.getParent();
        if (p instanceof MethodDeclaration md)              return md.getName() == name;
        if (p instanceof AbstractTypeDeclaration td)        return td.getName() == name;
        if (p instanceof VariableDeclarationFragment vdf)   return vdf.getName() == name;
        if (p instanceof SingleVariableDeclaration svd)     return svd.getName() == name;
        if (p instanceof EnumConstantDeclaration ecd)       return ecd.getName() == name;
        if (p instanceof AnnotationTypeMemberDeclaration a) return a.getName() == name;
        return false;
    }

    /** Parses {@code "name"} or {@code "name(Type1, Type2)"} into name + optional param list. */
    private record ParsedName(String name, List<String> paramTypes) {
        static ParsedName parse(String spec) {
            int paren = spec.indexOf('(');
            if (paren < 0) return new ParsedName(spec.trim(), null);
            String name = spec.substring(0, paren).trim();
            String inside = spec.substring(paren + 1, spec.lastIndexOf(')')).trim();
            List<String> types = inside.isEmpty() ? List.of() : List.of(inside.split("\\s*,\\s*"));
            return new ParsedName(name, types);
        }
    }
}
