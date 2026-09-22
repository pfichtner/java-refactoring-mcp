package com.github.pfichtner.project;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * Minimal Maven project model that provides JDT with what it needs:
 * source roots, dependency classpath, and Java version.
 *
 * Dependency resolution delegates to {@code mvn dependency:build-classpath}.
 * The result is cached after the first call.
 */
public class MavenProject implements JavaProject {

    /** Shared across the JVM: each unique project root pays mvn startup cost at most once. */
    private static final ConcurrentHashMap<Path, String[]> CLASSPATH_CACHE = new ConcurrentHashMap<>();

    private final Path root;
    private volatile String[] cachedClasspath;

    public MavenProject(Path root) {
        this.root = root.toAbsolutePath();
    }

    public Path root() {
        return root;
    }

    /**
     * Returns compile and test source roots. Defaults to {@code src/main/java}
     * and {@code src/test/java} (when present); honours a custom
     * {@code <sourceDirectory>} in pom.xml for the main root.
     */
    public List<Path> sourceRoots() {
        List<Path> roots = new ArrayList<>();
        try {
            Document doc = parsePom();
            XPath xpath = XPathFactory.newInstance().newXPath();
            String custom = xpath.evaluate(
                    "/project/build/sourceDirectory/text()", doc).trim();
            if (!custom.isEmpty()) {
                Path p = Path.of(custom);
                roots.add(p.isAbsolute() ? p : root.resolve(p));
            }
        } catch (Exception ignored) {
        }
        if (roots.isEmpty()) roots.add(root.resolve("src/main/java"));
        Path testRoot = root.resolve("src/test/java");
        if (Files.isDirectory(testRoot)) roots.add(testRoot);
        return List.copyOf(roots);
    }

    /**
     * Returns the Java release version declared in pom.xml.
     * Checks {@code maven.compiler.release}, then {@code maven.compiler.source}.
     * Defaults to {@code "21"}.
     */
    public String javaVersion() {
        try {
            Document doc = parsePom();
            XPath xpath = XPathFactory.newInstance().newXPath();
            String release = xpath.evaluate(
                    "/project/properties/maven.compiler.release/text()", doc).trim();
            if (!release.isEmpty()) return release;
            String source = xpath.evaluate(
                    "/project/properties/maven.compiler.source/text()", doc).trim();
            if (!source.isEmpty()) return source;
        } catch (Exception ignored) {
        }
        return "21";
    }

    /**
     * Resolves compile-scope dependencies to a classpath array by invoking
     * {@code mvn dependency:build-classpath}. Result is cached.
     */
    public String[] classpath() throws IOException, InterruptedException {
        if (cachedClasspath != null) return cachedClasspath;
        try {
            cachedClasspath = CLASSPATH_CACHE.computeIfAbsent(root, k -> {
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
        return cachedClasspath;
    }

    private String[] resolveClasspath() throws IOException, InterruptedException {
        // Fast path: if the pom has no parent and no dependencies, the classpath is empty.
        // This avoids a ~4 s Maven JVM startup for projects with no deps (e.g. test fixtures).
        if (hasNoDependenciesAndNoParent()) return new String[0];

        Path outputFile = Files.createTempFile("jdt-classpath-", ".txt");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "mvn", "-q", "dependency:build-classpath",
                    "-Dmdep.outputFile=" + outputFile.toAbsolutePath(),
                    "-Dmdep.includeScope=compile",
                    "-f", root.resolve("pom.xml").toAbsolutePath().toString()
            );
            pb.directory(root.toFile());
            pb.redirectErrorStream(true);
            int exit = pb.start().waitFor();
            if (exit != 0) {
                throw new IOException(
                        "mvn dependency:build-classpath failed (exit " + exit + ") for " + root);
            }
            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                return new String[0];
            }
            String cp = Files.readString(outputFile).trim();
            return cp.isEmpty() ? new String[0] : cp.split(File.pathSeparator);
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    private boolean hasNoDependenciesAndNoParent() {
        try {
            Document doc = parsePom();
            XPath xpath = XPathFactory.newInstance().newXPath();
            // Has a parent pom → may inherit deps — must invoke Maven
            String parent = xpath.evaluate("/project/parent/artifactId/text()", doc).trim();
            if (!parent.isEmpty()) return false;
            // Has any <dependency> element — must invoke Maven
            NodeList deps = (NodeList)
                    xpath.evaluate("/project/dependencies/dependency", doc,
                            XPathConstants.NODESET);
            return deps.getLength() == 0;
        } catch (Exception e) {
            return false; // fall back to running Maven when uncertain
        }
    }

    private Document parsePom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(null);
        return builder.parse(root.resolve("pom.xml").toFile());
    }
}
