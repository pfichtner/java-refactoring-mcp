package com.github.pfichtner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;

/**
 * Tests for the Maven project model and project-context rename.
 */
class MavenProjectTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void maven_project_reads_source_roots_and_java_version() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple");
        MavenProject project = new MavenProject(projectRoot);

        assertThat(project.javaVersion()).isEqualTo("21");

        var sourceRoots = project.sourceRoots();
        assertThat(sourceRoots.size()).isEqualTo(1);
        assertThat(sourceRoots.get(0).endsWith(Path.of("src/main/java"))).as("Expected src/main/java, got: " + sourceRoots.get(0)).isTrue();
        assertThat(Files.isDirectory(sourceRoots.get(0))).as("Source root must exist on disk: " + sourceRoots.get(0)).isTrue();
    }

    @Test
    void rename_local_variable_in_maven_project() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple");
        MavenProject project = new MavenProject(projectRoot);

        Path sourceFile = project.sourceRoots().get(0)
                .resolve("com/example/Calculator.java");
        String source = Files.readString(sourceFile);
        int offset = Fixtures.offsetOf(source, "int result") + "int ".length();

        String withProject    = JdtRenamer.renameLocalVariable(project, sourceFile, offset, "sum");
        String withoutProject = JdtRenamer.renameLocalVariable(source, "Calculator.java", offset, "sum");

        assertThat(withProject).isEqualTo(withoutProject);
    }

    @Test
    void honors_custom_sourceDirectory_from_pom(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>custom-src</artifactId>
                    <version>1.0</version>
                    <build>
                        <sourceDirectory>src/custom</sourceDirectory>
                    </build>
                </project>
                """);

        MavenProject project = new MavenProject(tmp);
        assertThat(project.sourceRoots()).isEqualTo(List.of(tmp.resolve("src/custom").toAbsolutePath()));
    }

    @Test
    void falls_back_to_maven_compiler_source_when_no_release(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>java11</artifactId>
                    <version>1.0</version>
                    <properties>
                        <maven.compiler.source>11</maven.compiler.source>
                    </properties>
                </project>
                """);

        assertThat(new MavenProject(tmp).javaVersion()).isEqualTo("11");
    }

    @Test
    void defaults_to_release_21_when_pom_has_no_compiler_version(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("pom.xml"), """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>no-version</artifactId>
                </project>
                """);

        assertThat(new MavenProject(tmp).javaVersion()).isEqualTo("21");
    }

    @Test
    void defaults_to_main_java_root_when_no_source_directory(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("pom.xml"), "<project/>");

        MavenProject project = new MavenProject(tmp);
        assertThat(project.sourceRoots()).isEqualTo(List.of(tmp.resolve("src/main/java").toAbsolutePath()));
    }

    @Test
    void empty_classpath_without_invoking_maven_when_no_deps_and_no_parent(@TempDir Path tmp)
            throws Exception {
        Files.writeString(tmp.resolve("pom.xml"), """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>no-deps</artifactId>
                </project>
                """);

        assertThat(new MavenProject(tmp).classpath()).isEmpty();
    }

    @Test
    void adds_test_source_root_when_present(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("pom.xml"), "<project/>");
        Files.createDirectories(tmp.resolve("src/test/java"));

        MavenProject project = new MavenProject(tmp);
        assertThat(project.sourceRoots())
                .contains(tmp.resolve("src/main/java").toAbsolutePath())
                .contains(tmp.resolve("src/test/java").toAbsolutePath());
    }
}
