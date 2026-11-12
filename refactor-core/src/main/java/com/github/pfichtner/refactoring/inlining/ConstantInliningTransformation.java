package com.github.pfichtner.refactoring.inlining;

import static org.eclipse.jdt.core.dom.Modifier.isFinal;
import static org.eclipse.jdt.core.dom.Modifier.isStatic;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

/**
 * Replaces references to compile-time constant fields ({@code static final} with a
 * non-null {@link IVariableBinding#getConstantValue()}) with their literal values.
 *
 * <p>Handles {@code boolean}, {@code char}, {@code String}, integer types
 * ({@code byte}, {@code short}, {@code int}, {@code long}), and floating-point
 * types ({@code float}, {@code double}). Skips {@code NaN} and {@code Infinity}
 * because they cannot be expressed as a finite literal.
 */
final class ConstantInliningTransformation {

    private ConstantInliningTransformation() {}

    /** Returns all constant field references in this unit, in reverse AST order. */
    static List<Name> collectCandidates(CompilationUnit cu) {
        List<Name> candidates = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(QualifiedName node) {
                if (isConstantRef(node)) candidates.add(node);
                // Don't descend: the child SimpleName would be a duplicate candidate.
                return false;
            }

            @Override
            public boolean visit(SimpleName node) {
                // Skip the name component inside a QualifiedName; handled by visit(QualifiedName).
                if (node.getParent() instanceof QualifiedName qn && qn.getName() == node) {
                    return false;
                }
                // Skip declaration sites (the field's own name in its declaration).
                if (node.isDeclaration()) return false;
                if (isConstantRef(node)) candidates.add(node);
                return false;
            }
        });
        Collections.reverse(candidates);
        return candidates;
    }

    private static boolean isConstantRef(Name name) {
        IBinding binding = name.resolveBinding();
        if (!(binding instanceof IVariableBinding var)) return false;
        if (!var.isField()) return false;
        if (!isFinal(var.getModifiers()) || !isStatic(var.getModifiers())) return false;
        return var.getConstantValue() != null;
    }

    /**
     * Returns the display name of the constant field for diagnostics.
     * For a {@code QualifiedName} this is the simple field name.
     */
    static String fieldName(Name fieldRef) {
        if (fieldRef instanceof QualifiedName qn) return qn.getName().getIdentifier();
        if (fieldRef instanceof SimpleName sn) return sn.getIdentifier();
        return fieldRef.toString();
    }

    /**
     * Replaces the constant field reference with its literal value.
     * Returns {@code null} when the constant value cannot be represented as a
     * Java literal (e.g. {@code NaN} or {@code Infinity}) or the binding
     * cannot be resolved.
     */
    static String tryInline(CompilationUnit cu, String source, Name fieldRef) {
        IBinding binding = fieldRef.resolveBinding();
        if (!(binding instanceof IVariableBinding var)) return null;
        Object constantValue = var.getConstantValue();
        if (constantValue == null) return null;

        AST ast = cu.getAST();
        Expression literal = createLiteral(ast, constantValue);
        if (literal == null) return null;

        ASTRewrite rewrite = ASTRewrite.create(ast);
        rewrite.replace(fieldRef, literal, null);

        try {
            Document document = new Document(source);
            TextEdit edits = rewrite.rewriteAST(document, null);
            edits.apply(document);
            return document.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply constant inline edits", e);
        }
    }

    private static Expression createLiteral(AST ast, Object value) {
        if (value instanceof Boolean b) {
            return ast.newBooleanLiteral(b);
        }
        if (value instanceof Character c) {
            CharacterLiteral lit = ast.newCharacterLiteral();
            lit.setCharValue(c);
            return lit;
        }
        if (value instanceof String s) {
            StringLiteral lit = ast.newStringLiteral();
            lit.setLiteralValue(s);
            return lit;
        }
        if (value instanceof Integer i) return intLiteral(ast, i);
        if (value instanceof Long l) return longLiteral(ast, l);
        if (value instanceof Byte b) return intLiteral(ast, b.intValue());
        if (value instanceof Short s) return intLiteral(ast, s.intValue());
        if (value instanceof Float f) return floatLiteral(ast, f);
        if (value instanceof Double d) return doubleLiteral(ast, d);
        return null;
    }

    private static Expression intLiteral(AST ast, int value) {
        if (value < 0) {
            // Cast to long before negating to avoid overflow for Integer.MIN_VALUE.
            return negated(ast, Long.toString(-(long) value));
        }
        return ast.newNumberLiteral(Integer.toString(value));
    }

    private static Expression longLiteral(AST ast, long value) {
        if (value < 0) {
            // Use BigInteger to handle Long.MIN_VALUE safely.
            return negated(ast, BigInteger.valueOf(value).abs() + "L");
        }
        return ast.newNumberLiteral(value + "L");
    }

    private static Expression floatLiteral(AST ast, float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return null;
        if (value < 0) {
            return negated(ast, Float.toString(-value) + "f");
        }
        return ast.newNumberLiteral(Float.toString(value) + "f");
    }

    private static Expression doubleLiteral(AST ast, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return null;
        if (value < 0) {
            return negated(ast, Double.toString(-value));
        }
        return ast.newNumberLiteral(Double.toString(value));
    }

    /** Wraps a positive number-literal token in a unary-minus prefix expression. */
    private static PrefixExpression negated(AST ast, String absToken) {
        PrefixExpression prefix = ast.newPrefixExpression();
        prefix.setOperator(PrefixExpression.Operator.MINUS);
        prefix.setOperand(ast.newNumberLiteral(absToken));
        return prefix;
    }
}
