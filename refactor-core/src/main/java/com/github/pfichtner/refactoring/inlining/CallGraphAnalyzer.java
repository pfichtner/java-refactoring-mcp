package com.github.pfichtner.refactoring.inlining;

import static com.github.pfichtner.refactoring.inlining.InliningSupport.enclosingMethod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;

/**
 * Directed call graph over the declared methods in a compilation unit or project snapshot.
 * Used to detect mutual recursion before inlining: if inlining callee B into caller A
 * would eventually re-invoke A (because A is reachable from B through the graph), the
 * transformation is rejected.
 */
final class CallGraphAnalyzer {

    /** callerKey → set of calleeKeys, restricted to declared candidates */
    private final Map<String, Set<String>> edges;

    CallGraphAnalyzer(Map<String, Set<String>> edges) {
        this.edges = edges;
    }

    /**
     * Builds a call graph from a single compilation unit.
     * Only edges where both caller and callee are declared in the CU are recorded.
     */
    static CallGraphAnalyzer from(CompilationUnit cu) {
        Set<String> declared = new HashSet<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                IMethodBinding b = node.resolveBinding();
                if (b != null) declared.add(b.getMethodDeclaration().getKey());
                return true;
            }
        });

        Map<String, Set<String>> edges = new HashMap<>();
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                IMethodBinding calleeB = node.resolveMethodBinding();
                if (calleeB == null) return true;
                String calleeKey = calleeB.getMethodDeclaration().getKey();
                if (!declared.contains(calleeKey)) return true;

                MethodDeclaration callerDecl = enclosingMethod(node);
                if (callerDecl == null) return true;
                IMethodBinding callerB = callerDecl.resolveBinding();
                if (callerB == null) return true;
                String callerKey = callerB.getMethodDeclaration().getKey();

                edges.computeIfAbsent(callerKey, k -> new HashSet<>()).add(calleeKey);
                return true;
            }
        });
        return new CallGraphAnalyzer(edges);
    }

    /**
     * Returns {@code true} if inlining {@code calleeKey} into {@code callerKey} would
     * create a cycle — i.e., if the caller is transitively reachable from the callee
     * through the current call graph.
     */
    boolean isMutuallyRecursive(String callerKey, String calleeKey) {
        if (callerKey.equals(calleeKey)) return false; // direct recursion, handled separately
        return isReachable(calleeKey, callerKey, new HashSet<>());
    }

    private boolean isReachable(String from, String target, Set<String> visited) {
        if (!visited.add(from)) return false;
        for (String callee : edges.getOrDefault(from, Set.of())) {
            if (callee.equals(target)) return true;
            if (isReachable(callee, target, visited)) return true;
        }
        return false;
    }
}
