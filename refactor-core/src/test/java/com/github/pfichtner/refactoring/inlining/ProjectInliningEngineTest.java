package com.github.pfichtner.refactoring.inlining;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.project.MavenProject;
import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

/**
 * Approval and semantic tests for project-wide aggressive inlining.
 * The approved markdown files are the specification for the rewritten sources.
 */
class ProjectInliningEngineTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void same_type_methods_are_inlined_across_files() throws Exception {
        MavenProject project = project("same-type");
        Map<String, String> inputs = inputs("same-type");

        ProjectInliningResult result = InliningEngine.inlineProject(project);

        assertThat(result.changedFiles()).hasSize(2);
        assertThat(result.transformationCount()).isEqualTo(2);
        assertThat(result.fixedPoint()).isTrue();

        Map<String, String> sources = merged(inputs, result.changedFiles());
        Path classes = compileProject(sources);
        assertThat(invoke(classes, "com.example.Alpha", "run")).isEqualTo(6);
        assertThat(invoke(classes, "com.example.Beta", "run")).isEqualTo(15);

        Approvals.verify(
            RefactoringStoryBoard.titled("Project inline: same-type methods across files")
                .inputProject(inputs)
                .refactoring("inline project", "`twice(3)` and `plusTen(5)`",
                        "two files change; cross-file bindings resolve, but each call and callee stay in the same type")
                .outputProject(result.changedFiles())
                .build()
        );
    }

    @Test
    void cross_type_instance_method_is_inlined_with_import() throws Exception {
        MavenProject project = project("cross-type-instance");
        Map<String, String> inputs = inputs("cross-type-instance");

        ProjectInliningResult result = InliningEngine.inlineProject(project);

        assertThat(result.changedFiles()).hasSize(1);
        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.fixedPoint()).isTrue();

        Map<String, String> sources = merged(inputs, result.changedFiles());
        Path classes = compileProject(sources);
        assertThat(invoke(classes, "com.example.app.App", "run")).hasToString("PT2S");

        Approvals.verify(
            RefactoringStoryBoard.titled("Project inline: cross-type instance method and import")
                .inputProject(inputs)
                .refactoring("inline project", "`defaultDuration()`",
                        "the inherited final method is inlined into the subclass; the Duration import is added to the caller")
                .outputProject(result.changedFiles())
                .build()
        );
    }

    @Test
    void cross_type_static_import_is_added_to_caller() throws Exception {
        MavenProject project = project("cross-type-static-import");
        Map<String, String> inputs = inputs("cross-type-static-import");

        ProjectInliningResult result = InliningEngine.inlineProject(project);

        assertThat(result.changedFiles()).hasSize(1);
        assertThat(result.transformationCount()).isEqualTo(1);

        Map<String, String> sources = merged(inputs, result.changedFiles());
        Path classes = compileProject(sources);
        assertThat(invoke(classes, "com.example.app.App", "run")).isEqualTo(5);

        Approvals.verify(
            RefactoringStoryBoard.titled("Project inline: cross-type static import")
                .inputProject(inputs)
                .refactoring("inline project", "`magnitude(-5)`",
                        "the copied body calls the statically imported Math.abs; the caller gets the same static import")
                .outputProject(result.changedFiles())
                .build()
        );
    }

    @Test
    void cross_type_static_call_is_rejected() throws Exception {
        MavenProject project = project("cross-type");
        Map<String, String> inputs = inputs("cross-type");

        ProjectInliningResult result = InliningEngine.inlineProject(project);

        assertThat(result.changedFiles()).isEmpty();
        assertThat(result.transformationCount()).isZero();
        assertThat(result.rejected())
                .anySatisfy(rejection -> assertThat(rejection.reason())
                        .contains("cross-type static inlining would remove the class-initialization trigger"));

        Approvals.verify(
            RefactoringStoryBoard.titled("Project inline — rejected: cross-type static call")
                .inputProject(inputs)
                .refactoring("inline project", "`Beta.compute(3)`",
                        "the callee is static in another type; inlining would remove its class-initialization trigger")
                .diagnostic(result.rejected().get(0).reason())
                .build()
        );
    }

    @Test
    void cross_type_super_usage_is_rejected() throws Exception {
        ProjectInliningResult result = InliningEngine.inlineProject(project("cross-type-super"));

        assertThat(result.changedFiles()).isEmpty();
        assertThat(result.rejected())
                .anySatisfy(rejection -> assertThat(rejection.reason())
                        .contains("cross-type callee body uses 'super'"));

        Approvals.verify(
            RefactoringStoryBoard.titled("Project inline — rejected: cross-type body uses super")
                .inputProject(inputs("cross-type-super"))
                .refactoring("inline project", "`describe()`",
                        "the copied body contains a super call; it would bind to App's superclass")
                .diagnostic(result.rejected().get(0).reason())
                .build()
        );
    }

    @Test
    void cross_type_private_member_is_rejected() throws Exception {
        ProjectInliningResult result = InliningEngine.inlineProject(project("cross-type-private"));

        assertThat(result.changedFiles()).isEmpty();
        assertThat(result.rejected())
                .anySatisfy(rejection -> assertThat(rejection.reason())
                        .contains("cross-type callee references private field"));

        Approvals.verify(
            RefactoringStoryBoard.titled("Project inline — rejected: cross-type private member")
                .inputProject(inputs("cross-type-private"))
                .refactoring("inline project", "`peek()`",
                        "the copied body reads a private field of the superclass; the subclass cannot access it")
                .diagnostic(result.rejected().get(0).reason())
                .build()
        );
    }

    @Test
    void cross_type_import_conflict_is_rejected() throws Exception {
        ProjectInliningResult result = InliningEngine.inlineProject(project("cross-type-conflict"));

        assertThat(result.changedFiles()).isEmpty();
        assertThat(result.rejected())
                .anySatisfy(rejection -> assertThat(rejection.reason())
                        .contains("import conflict"));

        Approvals.verify(
            RefactoringStoryBoard.titled("Project inline — rejected: cross-type import conflict")
                .inputProject(inputs("cross-type-conflict"))
                .refactoring("inline project", "`defaultSeconds()`",
                        "the callee needs java.time.Duration, but the caller already imports a different Duration")
                .diagnostic(result.rejected().get(0).reason())
                .build()
        );
    }

    @Test
    void project_batch_equals_per_file_inline_all() throws Exception {
        MavenProject project = project("same-type");
        ProjectInliningResult result = InliningEngine.inlineProject(project);

        for (Path file : result.changedFiles().keySet()) {
            String original = Files.readString(file);
            String expected = InliningEngine.inlineAll(original, file.getFileName().toString()).source();
            assertThat(result.changedFiles().get(file))
                    .as("project result for %s must match single-file inlineAll", file.getFileName())
                    .isEqualTo(expected);
        }
    }

    @Test
    void dry_run_does_not_modify_project_files() throws Exception {
        MavenProject project = project("same-type");
        List<Path> files = new ArrayList<>();
        for (Path root : project.sourceRoots()) {
            try (var stream = Files.walk(root)) {
                stream.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
        }
        Map<Path, byte[]> before = new TreeMap<>();
        for (Path file : files) before.put(file, Files.readAllBytes(file));

        InliningEngine.inlineProject(project);

        for (Path file : files) {
            assertThat(Files.readAllBytes(file))
                    .as("dry run must leave %s untouched", file.getFileName())
                    .isEqualTo(before.get(file));
        }
    }

    @Test
    void max_transformations_stops_project_loop() throws Exception {
        ProjectInliningResult result = InliningEngine.inlineProject(
                project("same-type"), new InlineConfig(1, 1_000_000));

        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.changedFiles()).hasSize(1);
        assertThat(result.fixedPoint()).isFalse();
    }

    @Test
    void unused_declarations_are_removed_after_project_inlining() throws Exception {
        MavenProject project = project("same-type");
        Map<String, String> inputs = inputs("same-type");

        ProjectInliningResult result = InliningEngine.inlineProject(
                project, new InlineConfig(100, 100_000, true));

        assertThat(result.changedFiles()).hasSize(2);
        assertThat(result.fixedPoint()).isTrue();
        assertThat(result.removed()).hasSize(2);
        assertThat(result.removed())
                .anySatisfy(r -> assertThat(r.methodName()).isEqualTo("twice"))
                .anySatisfy(r -> assertThat(r.methodName()).isEqualTo("plusTen"));

        Map<String, String> sources = merged(inputs, result.changedFiles());
        assertThat(sources.get("Alpha.java")).doesNotContain("private int twice");
        assertThat(sources.get("Beta.java")).doesNotContain("private int plusTen");

        Path classes = compileProject(sources);
        assertThat(invoke(classes, "com.example.Alpha", "run")).isEqualTo(6);
        assertThat(invoke(classes, "com.example.Beta", "run")).isEqualTo(15);
    }

    @Test
    void code_growth_budget_blocks_all_candidates() throws Exception {
        ProjectInliningResult result = InliningEngine.inlineProject(
                project("same-type"), new InlineConfig(20, 0));

        assertThat(result.transformationCount()).isZero();
        assertThat(result.fixedPoint()).isFalse();
        assertThat(result.rejected())
                .anySatisfy(rejection -> assertThat(rejection.reason())
                        .contains("code growth limit exceeded"));
    }

    private MavenProject project(String name) throws Exception {
        return new MavenProject(fixtures.projectPath("projects/inline-aggressive-project/" + name));
    }

    private Map<String, String> inputs(String name) throws Exception {
        return fixtures.loadProjectSources(
                "projects/inline-aggressive-project/" + name + "/src/main/java");
    }

    private static Map<String, String> merged(
            Map<String, String> inputs, Map<Path, String> changed) {
        Map<String, String> merged = new TreeMap<>(inputs);
        changed.forEach((path, source) -> merged.put(path.getFileName().toString(), source));
        return merged;
    }

    private static Path compileProject(Map<String, String> sources) throws Exception {
        Path root = Files.createTempDirectory("inline-project-compile");
        Path src = root.resolve("src");
        Path classes = root.resolve("classes");
        Files.createDirectories(src);
        Files.createDirectories(classes);

        List<String> args = new ArrayList<>();
        args.add("-d");
        args.add(classes.toString());
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path file = src.resolve(entry.getKey());
            Files.writeString(file, entry.getValue());
            args.add(file.toString());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler must be available").isNotNull();
        int exitCode = compiler.run(null, null, null, args.toArray(String[]::new));
        assertThat(exitCode).as("transformed project must compile").isZero();
        return classes;
    }

    private static Object invoke(Path classes, String className, String methodName)
            throws Exception {
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL()}, ClassLoader.getSystemClassLoader())) {
            Class<?> type = Class.forName(className, true, loader);
            Object instance = type.getDeclaredConstructor().newInstance();
            return type.getMethod(methodName).invoke(instance);
        }
    }
}
