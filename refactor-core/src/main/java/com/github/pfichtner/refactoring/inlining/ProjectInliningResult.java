package com.github.pfichtner.refactoring.inlining;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of a project-wide inlining run.
 *
 * @param changedFiles  files whose source changed, in deterministic order
 * @param applied       transformations that were applied, in order
 * @param rejected      candidates that were considered but refused
 * @param removed       declarations removed because all their call sites were inlined
 *                      (only populated when {@link InlineConfig#removeUnusedDeclarations()} is true)
 * @param fixedPoint    {@code true} when the run stopped because no more candidates applied,
 *                      {@code false} when a budget stopped it
 */
public record ProjectInliningResult(
        Map<Path, String> changedFiles,
        List<Applied> applied,
        List<Rejected> rejected,
        List<Removed> removed,
        boolean fixedPoint) {

    public ProjectInliningResult {
        changedFiles = Collections.unmodifiableMap(new LinkedHashMap<>(changedFiles));
        applied = List.copyOf(applied);
        rejected = List.copyOf(rejected);
        removed = List.copyOf(removed);
    }

    public int transformationCount() {
        return applied.size();
    }

    public record Applied(Path path, String methodName, int offset) {}

    public record Rejected(Path path, String methodName, int offset, String reason) {}

    public record Removed(Path path, String methodName) {}
}
