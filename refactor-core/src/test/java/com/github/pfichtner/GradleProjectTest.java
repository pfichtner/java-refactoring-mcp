package com.github.pfichtner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.pfichtner.project.GradleProject;
import com.github.pfichtner.support.Fixtures;

/**
 * Tests for the Gradle project model and project-context rename.
 */
class GradleProjectTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void gradle_project_reads_source_roots_and_java_version() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple-gradle");
        GradleProject project = new GradleProject(projectRoot);

        assertThat(project.javaVersion()).isEqualTo("21");

        var sourceRoots = project.sourceRoots();
        assertThat(sourceRoots.size()).isEqualTo(1);
        assertThat(sourceRoots.get(0).endsWith(Path.of("src/main/java"))).as("Expected src/main/java, got: " + sourceRoots.get(0)).isTrue();
        assertThat(Files.isDirectory(sourceRoots.get(0))).as("Source root must exist on disk: " + sourceRoots.get(0)).isTrue();
    }

    @Test
    void rename_local_variable_in_gradle_project() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple-gradle");
        GradleProject project = new GradleProject(projectRoot);

        Path sourceFile = project.sourceRoots().get(0)
                .resolve("com/example/Calculator.java");
        String source = Files.readString(sourceFile);
        int offset = Fixtures.offsetOf(source, "int result") + "int ".length();

        String withProject    = JdtRenamer.renameLocalVariable(project, sourceFile, offset, "sum");
        String withoutProject = JdtRenamer.renameLocalVariable(source, "Calculator.java", offset, "sum");

        assertThat(withProject).isEqualTo(withoutProject);
    }

    @Test
    void reads_version_from_build_gradle_kts(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("build.gradle.kts"), """
                plugins { java }
                java {
                    sourceCompatibility = JavaVersion.VERSION_17
                }
                """);

        GradleProject project = new GradleProject(tmp);
        assertThat(project.javaVersion()).isEqualTo("17");
        assertThat(project.sourceRoots()).isEqualTo(List.of(tmp.resolve("src/main/java").toAbsolutePath()));
    }

    @Test
    void honors_single_string_src_dirs_declaration(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("build.gradle"), """
                plugins { id 'java' }
                sourceSets {
                    main { java.srcDirs = 'src/gen' }
                }
                """);

        GradleProject project = new GradleProject(tmp);
        assertThat(project.sourceRoots()).isEqualTo(List.of(tmp.resolve("src/gen").toAbsolutePath()));
    }

    @Test
    void defaults_to_release_21_and_main_java_root_when_no_build_script(@TempDir Path tmp) {
        GradleProject project = new GradleProject(tmp);

        assertThat(project.javaVersion()).isEqualTo("21");
        assertThat(project.sourceRoots()).isEqualTo(List.of(tmp.resolve("src/main/java").toAbsolutePath()));
    }

    @Test
    void defaults_to_main_java_root_when_no_src_dirs_declared(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("build.gradle"), "plugins { id 'java' }");

        GradleProject project = new GradleProject(tmp);
        assertThat(project.sourceRoots()).isEqualTo(List.of(tmp.resolve("src/main/java").toAbsolutePath()));
    }

    @Test
    void adds_test_source_root_when_present(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("build.gradle"), "plugins { id 'java' }");
        Files.createDirectories(tmp.resolve("src/test/java"));

        GradleProject project = new GradleProject(tmp);
        assertThat(project.sourceRoots())
                .contains(tmp.resolve("src/main/java").toAbsolutePath())
                .contains(tmp.resolve("src/test/java").toAbsolutePath());
    }

    @Test
    void empty_classpath_when_build_script_has_no_dependencies(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("build.gradle"), """
                plugins { id 'java' }
                sourceCompatibility = '21'
                """);

        assertThat(new GradleProject(tmp).classpath()).isEmpty();
    }
}
