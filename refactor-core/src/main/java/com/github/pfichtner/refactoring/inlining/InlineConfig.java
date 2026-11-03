package com.github.pfichtner.refactoring.inlining;

/**
 * Budgets for iterative inlining. The spike only enforces the first two;
 * depth counters are listed here because they are part of the proposed design.
 */
public record InlineConfig(int maxTransformations, int maxCodeGrowthChars) {

    public static final InlineConfig DEFAULT = new InlineConfig(100, 100_000);

    public InlineConfig {
        if (maxTransformations < 0) {
            throw new IllegalArgumentException("maxTransformations must be >= 0");
        }
        if (maxCodeGrowthChars < 0) {
            throw new IllegalArgumentException("maxCodeGrowthChars must be >= 0");
        }
    }
}
