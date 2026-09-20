package com.github.pfichtner.project;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Gradle project model providing JDT with source roots, Java version,
 * and a compile-scope classpath.
 *
 * Source roots and Java version are read from {@code build.gradle} /
 * {@code build.gradle.kts} via heuristic text matching (not full Gradle parsing).
 * Classpath resolution delegates to Gradle via a temporary init script, analogous
 * to how {@link MavenProject} invokes {@code mvn dependency:build-classpath}.
 */
public class GradleProject implements JavaProject {

    private static final ConcurrentHashMap<Path, String[]> CLASSPATH_CACHE = new ConcurrentHashMap<>();

    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile(
            "(?:sourceCompatibility|targetCompatibility|release)\\s*[=:]?\\s*(?:JavaVersion\\.VERSION_)?(\\d+)");

    private final Path root;
    private volatile String[] cachedClasspath;

    public GradleProject(Path root) {
        this.root = root.toAbsolutePath();
    }

    @Override
    public Path root() {
        return root;
    }

    /**
     * Returns source roots. Defaults to {@code src/main/java} + {@code src/test/java}.
     * Honours simple {@code srcDirs} declarations in build.gradle when they list a
     * single quoted string path — complex cases fall back to the default.
     */
    @Override
    public List<Path> sourceRoots() {
        List<Path> roots = new ArrayList<>();
        try {
            String script = readBuildScript();
            if (script != null) {
                Pattern srcDirs = Pattern.compile(
                        "srcDirs\\s*(?:=|\\()\\s*['\"]([^'\"]+)['\"]");
                Matcher m = srcDirs.matcher(script);
                while (m.find()) {
                    Path p = Path.of(m.group(1));
                    roots.add(p.isAbsolute() ? p : root.resolve(p));
                }
            }
        } catch (Exception ignored) {
        }
        if (roots.isEmpty()) roots.add(root.resolve("src/main/java"));
        Path testRoot = root.resolve("src/test/java");
        if (Files.isDirectory(testRoot)) roots.add(testRoot);
        return List.copyOf(roots);
    }

    /**
     * Returns the Java release version from the build script.
     * Looks for {@code sourceCompatibility}, {@code targetCompatibility}, or
     * {@code options.release} assignments. Defaults to {@code "21"}.
     */
    @Override
    public String javaVersion() {
        try {
            String script = readBuildScript();
            if (script != null) {
                Matcher m = JAVA_VERSION_PATTERN.matcher(script);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) {
        }
        return "21";
    }

    /**
     * Resolves compile-scope dependencies by running Gradle with a temporary init
     * script that prints the {@code compileClasspath} jar paths. Result is cached.
     */
    @Override
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
        if (hasNoDependencies()) return new String[0];

        String initScript = """
                allprojects {
                    task _jdtClasspath {
                        doLast {
                            try {
                                def cp = configurations.compileClasspath
                                    .resolvedConfiguration.files
                                println cp*.absolutePath.join(File.pathSeparator)
                            } catch (ignored) {}
                        }
                    }
                }
                """;
        Path initFile = Files.createTempFile("jdt-gradle-init-", ".gradle");
        try {
            Files.writeString(initFile, initScript);
            String gradle = gradleExecutable();
            ProcessBuilder pb = new ProcessBuilder(
                    gradle, "-q", "_jdtClasspath",
                    "--init-script", initFile.toAbsolutePath().toString()
            );
            pb.directory(root.toFile());
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes()).trim();
            int exit = proc.waitFor();
            if (exit != 0) {
                throw new IOException(
                        "Gradle _jdtClasspath task failed (exit " + exit + ") for " + root);
            }
            if (output.isEmpty()) return new String[0];
            // last non-empty line is the classpath (init script may emit warnings before)
            String cpLine = output.lines()
                    .filter(l -> !l.isBlank())
                    .reduce((a, b) -> b)
                    .orElse("").trim();
            return cpLine.isEmpty() ? new String[0] : cpLine.split(File.pathSeparator);
        } finally {
            Files.deleteIfExists(initFile);
        }
    }

    private boolean hasNoDependencies() {
        try {
            String script = readBuildScript();
            if (script == null) return true;
            // Presence of a dependencies {} block indicates external deps
            return !script.contains("dependencies");
        } catch (Exception e) {
            return false;
        }
    }

    private String gradleExecutable() {
        Path wrapper = root.resolve(System.getProperty("os.name", "").toLowerCase().contains("win")
                ? "gradlew.bat" : "gradlew");
        return Files.isExecutable(wrapper) ? wrapper.toAbsolutePath().toString() : "gradle";
    }

    private String readBuildScript() throws IOException {
        Path groovy = root.resolve("build.gradle");
        if (Files.isRegularFile(groovy)) return Files.readString(groovy);
        Path kts = root.resolve("build.gradle.kts");
        if (Files.isRegularFile(kts)) return Files.readString(kts);
        return null;
    }
}
