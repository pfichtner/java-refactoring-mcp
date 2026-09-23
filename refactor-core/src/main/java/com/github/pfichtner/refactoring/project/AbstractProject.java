package com.github.pfichtner.refactoring.project;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/** Shared classpath-caching infrastructure for Maven and Gradle projects. */
abstract class AbstractProject implements JavaProject {

    private static final ConcurrentHashMap<Path, String[]> CLASSPATH_CACHE = new ConcurrentHashMap<>();

    protected final Path root;

    protected AbstractProject(Path root) {
        this.root = root.toAbsolutePath();
    }

    @Override
    public Path root() { return root; }

    @Override
    public String[] classpath() throws IOException, InterruptedException {
        try {
            return CLASSPATH_CACHE.computeIfAbsent(root, k -> {
                try { return resolveClasspath(); }
                catch (IOException e)          { throw new UncheckedIOException(e); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InterruptedException ie) throw ie;
            throw e;
        }
    }

    protected abstract String[] resolveClasspath() throws IOException, InterruptedException;
}
