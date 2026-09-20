package com.github.pfichtner;

import com.github.pfichtner.project.ExplicitProject;
import com.github.pfichtner.support.Fixtures;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ExplicitProject — project constructed from caller-supplied source roots,
 * Java version, and classpath, with no build-file parsing.
 */
class ExplicitProjectTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void explicit_project_uses_provided_source_roots_and_version() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple");
        Path srcRoot     = projectRoot.resolve("src/main/java");

        ExplicitProject project = new ExplicitProject(projectRoot, List.of(srcRoot), "17", new String[0]);

        assertThat(project.javaVersion()).isEqualTo("17");
        assertThat(project.sourceRoots()).isEqualTo(List.of(srcRoot.toAbsolutePath()));
        assertThat(project.classpath().length).isEqualTo(0);
        assertThat(Files.isDirectory(project.sourceRoots().get(0))).isTrue();
    }

    @Test
    void rename_local_variable_with_explicit_project() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/simple");
        Path srcRoot     = projectRoot.resolve("src/main/java");
        ExplicitProject project = new ExplicitProject(projectRoot, List.of(srcRoot), "21", new String[0]);

        Path sourceFile = srcRoot.resolve("com/example/Calculator.java");
        String source   = Files.readString(sourceFile);
        int offset      = Fixtures.offsetOf(source, "int result") + "int ".length();

        String withProject    = JdtRenamer.renameLocalVariable(project, sourceFile, offset, "sum");
        String withoutProject = JdtRenamer.renameLocalVariable(source, "Calculator.java", offset, "sum");

        assertThat(withProject).isEqualTo(withoutProject);
    }
}
