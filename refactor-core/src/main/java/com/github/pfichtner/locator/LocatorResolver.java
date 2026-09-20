package com.github.pfichtner.locator;

import com.github.pfichtner.JdtRenamer;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

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
        };
    }

    // -------------------------------------------------------------------------
    // Per-kind resolution
    // -------------------------------------------------------------------------

    private static int resolveMethod(String nameSpec, String className,
                                     String source, String unitName) {
        ParsedName parsed = ParsedName.parse(nameSpec);
        CompilationUnit cu = parse(source, unitName);
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
        CompilationUnit cu = parse(source, unitName);
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

        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Field '" + name + "' not found in " + unitName);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Ambiguous: multiple fields named '" + name + "' in " + unitName +
                    ". Specify 'class' to narrow the scope.");
        }
        return matches.get(0).getName().getStartPosition();
    }

    private static int resolveType(String name, String source, String unitName) {
        CompilationUnit cu = parse(source, unitName);
        List<AbstractTypeDeclaration> matches = new ArrayList<>();

        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                if (node.getName().getIdentifier().equals(name)) matches.add(node);
                return true;
            }

            @Override
            public boolean visit(EnumDeclaration node) {
                if (node.getName().getIdentifier().equals(name)) matches.add(node);
                return true;
            }

            @Override
            public boolean visit(AnnotationTypeDeclaration node) {
                if (node.getName().getIdentifier().equals(name)) matches.add(node);
                return true;
            }
        });

        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Type '" + name + "' not found in " + unitName);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Ambiguous: multiple types named '" + name + "' in " + unitName);
        }
        return matches.get(0).getName().getStartPosition();
    }

    private static int resolveParameter(String methodSpec, String paramName,
                                        String source, String unitName) {
        ParsedName parsed = ParsedName.parse(methodSpec);
        CompilationUnit cu = parse(source, unitName);
        List<SingleVariableDeclaration> matches = new ArrayList<>();

        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (!node.getName().getIdentifier().equals(parsed.name())) return true;
                if (parsed.paramTypes() != null && !paramTypesMatch(node, parsed.paramTypes())) return true;
                @SuppressWarnings("unchecked")
                List<SingleVariableDeclaration> params = node.parameters();
                for (SingleVariableDeclaration p : params) {
                    if (p.getName().getIdentifier().equals(paramName)) {
                        matches.add(p);
                    }
                }
                return true;
            }
        });

        if (matches.isEmpty()) {
            throw new IllegalArgumentException(
                    "Parameter '" + paramName + "' in method '" + methodSpec + "' not found in " + unitName);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "Ambiguous: parameter '" + paramName + "' in method '" + methodSpec + "' matches multiple locations in " + unitName +
                    ". Specify parameter types in method spec, e.g. '" + parsed.name() + "(String, int)'");
        }
        return matches.get(0).getName().getStartPosition();
    }

    private static int resolveVariable(String name, String methodSpec, String source, String unitName) {
        ParsedName parsed = ParsedName.parse(methodSpec);
        CompilationUnit cu = parse(source, unitName);

        // 1. Find the enclosing method/constructor
        List<MethodDeclaration> methods = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (!node.getName().getIdentifier().equals(parsed.name())) return true;
                if (parsed.paramTypes() != null && !paramTypesMatch(node, parsed.paramTypes())) return true;
                methods.add(node);
                return true;
            }
        });

        if (methods.isEmpty())
            throw new IllegalArgumentException(
                    "Method '" + methodSpec + "' not found in " + unitName);
        if (methods.size() > 1)
            throw new IllegalArgumentException(
                    "Ambiguous: multiple methods named '" + methodSpec + "' in " + unitName +
                    ". Specify parameter types, e.g. \"" + parsed.name() + "(int, String)\".");

        // 2. Within that method, find the local variable
        List<VariableDeclarationFragment> vars = new ArrayList<>();
        methods.get(0).accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                if (node.getName().getIdentifier().equals(name))
                    vars.add(node);
                return true;
            }
        });

        if (vars.isEmpty())
            throw new IllegalArgumentException(
                    "Local variable '" + name + "' not found in method '" + methodSpec + "' in " + unitName);
        if (vars.size() > 1)
            throw new IllegalArgumentException(
                    "Ambiguous: multiple variables named '" + name + "' in method '" + methodSpec +
                    "' in " + unitName + ". Use --line or --line/--column to disambiguate.");
        return vars.get(0).getName().getStartPosition();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static CompilationUnit parse(String source, String unitName) {
        ASTParser parser = ASTParser.newParser(AST.JLS21);
        parser.setSource(source.toCharArray());
        parser.setUnitName(unitName);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        return (CompilationUnit) parser.createAST(null);
    }

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
        for (int i = 0; i < paramTypes.size(); i++) {
            String expected = paramTypes.get(i).trim();
            String actual = params.get(i).getType().toString();
            if (!actual.equals(expected)) return false;
        }
        return true;
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
        CompilationUnit cu = parse(source, unitName);
        List<SimpleName> matches = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(SimpleName node) {
                if (isDeclarationName(node) && cu.getLineNumber(node.getStartPosition()) == line)
                    matches.add(node);
                return true;
            }
        });
        if (matches.isEmpty())
            throw new IllegalArgumentException(
                    "No named element at line " + line + " in " + unitName +
                    ". Use --variable, --method, or another name-based locator.");
        if (matches.size() > 1)
            throw new IllegalArgumentException(
                    "Ambiguous: " + matches.size() + " elements at line " + line +
                    " in " + unitName + ". Specify --column or a name-based locator.");
        return matches.get(0).getStartPosition();
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
