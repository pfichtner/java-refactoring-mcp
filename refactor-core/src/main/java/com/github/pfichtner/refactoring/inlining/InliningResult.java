package com.github.pfichtner.refactoring.inlining;

import java.util.List;

/**
 * Result of an iterative inlining run.
 *
 * @param source        rewritten source after all applied transformations
 * @param applied       transformations that were applied, in order
 * @param rejected      candidates that were considered but refused
 * @param fixedPoint    {@code true} when the run stopped because no more candidates applied,
 *                      {@code false} when a budget stopped it
 */
public record InliningResult(
        String source,
        List<Applied> applied,
        List<Rejected> rejected,
        boolean fixedPoint) {

    public InliningResult {
        applied = List.copyOf(applied);
        rejected = List.copyOf(rejected);
    }

    public int transformationCount() {
        return applied.size();
    }

    public record Applied(String methodName, int offset) {}

    public record Rejected(String methodName, int offset, String reason) {}
}
