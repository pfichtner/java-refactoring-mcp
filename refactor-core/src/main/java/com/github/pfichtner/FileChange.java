package com.github.pfichtner;

import java.nio.file.Path;

/** A source file whose content and/or location changed after a refactoring. */
public record FileChange(Path oldPath, Path newPath, String newSource) {
    /** {@code true} when the file also moves to a new path on disk. */
    public boolean pathChanged() { return !oldPath.equals(newPath); }
}
