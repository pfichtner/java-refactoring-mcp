package com.github.pfichtner.refactoring;

/**
 * A compilation unit's source text and its JDT unit name (typically the file's simple name,
 * e.g. {@code "Foo.java"}).  These two values always come from the same file and are
 * required together by every single-file refactoring operation.
 */
public record SourceUnit(String source, String unitName) {}
