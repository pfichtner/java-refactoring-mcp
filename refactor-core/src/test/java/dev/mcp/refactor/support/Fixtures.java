package dev.mcp.refactor.support;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utilities for loading test fixture files and computing source positions.
 */
public class Fixtures {

    private final Class<?> testClass;

    public Fixtures(Class<?> testClass) {
        this.testClass = testClass;
    }

    /** Loads a single fixture file from {@code src/test/resources/fixtures/<path>}. */
    public String load(String path) throws IOException, URISyntaxException {
        var url = testClass.getClassLoader().getResource("fixtures/" + path);
        if (url == null) {
            throw new IllegalStateException("Fixture not found: fixtures/" + path);
        }
        return Files.readString(Path.of(url.toURI()));
    }

    /**
     * Returns the character offset of the first occurrence of {@code token} in {@code source}.
     * Throws clearly if the token is absent.
     */
    public static int offsetOf(String source, String token) {
        int idx = source.indexOf(token);
        if (idx < 0) {
            throw new IllegalStateException("Token not found in source: " + token);
        }
        return idx;
    }

    /** Returns a human-readable position string {@code "line N, col M"} for a character offset. */
    public static String lineCol(String source, int offset) {
        String before = source.substring(0, offset);
        int line = (int) before.chars().filter(c -> c == '\n').count() + 1;
        int col = offset - before.lastIndexOf('\n');
        return "line " + line + ", col " + col;
    }
}
