package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.findInvocation;
import static com.github.pfichtner.refactoring.inlining.InliningSupport.findMethodDeclaration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;

import com.github.pfichtner.refactoring.JdtProjectSources;

/**
 * Isolated technical spike: iterative inlining of local methods in one
 * compilation unit.
 *
 * <p>Each pass re-parses the rewritten source so bindings and positions are
 * fresh, then applies the first safe candidate. The loop stops at a fixed point
 * (no candidate applies) or when a budget is exhausted.
 */
public final class InliningEngine {

    private InliningEngine() {}

    /** Inlines the call at {@code offset}, or throws if the transformation is unsafe. */
    public static String inlineAt(String source, String unitName, int offset) {
        CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(source, unitName);
        MethodInvocation call = findInvocation(cu, offset);
        IMethodBinding binding = call.resolveMethodBinding();
        if (binding == null) {
            throw new InlineRejectedException("Cannot resolve method call at offset " + offset + ".");
        }
        MethodDeclaration decl = findMethodDeclaration(cu, binding);
        if (decl == null) {
            throw new InlineRejectedException(
                    "Method '" + binding.getName() + "' is not declared in this compilation unit.");
        }
        String reason = SemanticSafetyAnalyzer.rejectionReason(cu, call, decl, binding);
        if (reason != null) {
            throw new InlineRejectedException(reason);
        }
        return InlineMethodTransformation.apply(cu, source, call, decl, binding);
    }

    public static InliningResult inlineAll(String source, String unitName) {
        return inlineAll(source, unitName, InlineConfig.DEFAULT);
    }

    public static InliningResult inlineAll(
            String source, String unitName, InlineConfig config) {

        String current = source;
        List<InliningResult.Applied> applied = new ArrayList<>();
        List<InliningResult.Rejected> rejected = new ArrayList<>();
        Set<String> rejectedKeys = new HashSet<>();
        boolean fixedPoint = false;

        while (applied.size() < config.maxTransformations()) {
            CompilationUnit cu = JdtProjectSources.parseUnitWithBindings(current, unitName);
            boolean progress = false;

            for (MethodInvocation call : collectCalls(cu)) {
                IMethodBinding binding = call.resolveMethodBinding();
                if (binding == null) continue;

                MethodDeclaration decl = findMethodDeclaration(cu, binding);
                if (decl == null) continue;

                String methodName = binding.getName();
                int offset = call.getStartPosition();
                String reason = SemanticSafetyAnalyzer.rejectionReason(cu, call, decl, binding);
                if (reason != null) {
                    addRejection(rejected, rejectedKeys, methodName, offset, reason);
                    continue;
                }

                try {
                    String next = InlineMethodTransformation.apply(cu, current, call, decl, binding);
                    if (next.equals(current)) {
                        addRejection(rejected, rejectedKeys, methodName, offset,
                                "transformation produced no change");
                        continue;
                    }
                    int growth = next.length() - source.length();
                    if (growth > config.maxCodeGrowthChars()) {
                        addRejection(rejected, rejectedKeys, methodName, offset,
                                "code growth limit exceeded (" + growth + " > "
                                        + config.maxCodeGrowthChars() + " chars)");
                        continue;
                    }
                    current = next;
                    applied.add(new InliningResult.Applied(methodName, offset));
                    progress = true;
                    break;
                } catch (InlineRejectedException e) {
                    addRejection(rejected, rejectedKeys, methodName, offset, e.getMessage());
                }
            }

            if (!progress) {
                fixedPoint = true;
                break;
            }
        }

        return new InliningResult(current, applied, rejected, fixedPoint);
    }

    private static List<MethodInvocation> collectCalls(CompilationUnit cu) {
        List<MethodInvocation> calls = new ArrayList<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                calls.add(node);
                return true;
            }
        });
        Collections.reverse(calls);
        return calls;
    }

    private static void addRejection(
            List<InliningResult.Rejected> rejected, Set<String> keys,
            String methodName, int offset, String reason) {

        if (keys.add(methodName + '\u0000' + reason)) {
            rejected.add(new InliningResult.Rejected(methodName, offset, reason));
        }
    }
}
