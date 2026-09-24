package com.github.pfichtner.refactoring.mcp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.github.pfichtner.refactoring.locator.Locator;
import com.github.pfichtner.refactoring.locator.LocatorResolver;

/**
 * A source file to be refactored: its {@link Path} plus its contents, which
 * are read lazily from disk on first access to {@link #content()} and cached
 * afterwards.
 *
 * <p>Note: this is a plain class, not a record, because records cannot hold
 * mutable instance state for the cache.
 */
public final class SourceFile {

    private final Path path;
    private String content;

    public SourceFile(Path path) {
        this.path = Objects.requireNonNull(path);
    }

    /** Returns the path of the source file. */
    public Path path() {
        return path;
    }

    /** Returns the file contents, read from {@link #path()} on first call and cached afterwards. */
    public String content() {
        String cached = content;
        if (cached == null) {
            try {
                cached = Files.readString(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            content = cached;
        }
        return cached;
    }

    
    public int resolve(Locator locator) {
    	return LocatorResolver.resolve(locator, content(), path().getFileName().toString());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SourceFile that && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return "SourceFile[" + path + "]";
    }

}