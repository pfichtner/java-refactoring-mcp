package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Approval tests for Rename Package.
 */
class RenamePackageTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void rename_package_updates_declarations_imports_and_paths() throws Exception {
        var project = new MavenProject(fixtures.projectPath("projects/rename-package"));
        JdtRenamePackage.Result result = JdtRenamePackage.renamePackage(
                project, "com.example.service", "com.example.util");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-package/src/main/java");

        // Build newPath → newSource map for outputProject display
        Map<Path, String> outputs = new LinkedHashMap<>();
        result.changedFiles().stream()
                .sorted(Comparator.comparing(a -> a.newPath().getFileName().toString()))
                .forEach(fc -> outputs.put(fc.newPath(), fc.newSource()));

        Approvals.verify(
            RenameStoryBoard.titled("Rename package: com.example.service → com.example.util")
                .inputProject(inputs)
                .refactoring("rename package",
                        "`com.example.service` → `com.example.util`",
                        "package decl in Calculator.java + single-class and wildcard imports in App.java")
                .outputProject(outputs)
                .build()
        );
    }

    @Test
    void rename_package_calculator_gets_new_path() throws Exception {
        var project = new MavenProject(fixtures.projectPath("projects/rename-package"));
        JdtRenamePackage.Result result = JdtRenamePackage.renamePackage(
                project, "com.example.service", "com.example.util");

        JdtRenamePackage.FileChange calcChange = result.changedFiles().stream()
                .filter(fc -> fc.oldPath().getFileName().toString().equals("Calculator.java"))
                .findFirst().orElseThrow();

        assertThat(calcChange.newPath().toString()).as("New path: " + calcChange.newPath()).contains("com/example/util/Calculator.java");
        assertThat(calcChange.pathChanged()).as("Path must change").isTrue();
        assertThat(calcChange.newSource()).as("Package declaration must be updated").contains("package com.example.util;");
    }

    @Test
    void rename_package_app_imports_are_updated() throws Exception {
        var project = new MavenProject(fixtures.projectPath("projects/rename-package"));
        JdtRenamePackage.Result result = JdtRenamePackage.renamePackage(
                project, "com.example.service", "com.example.util");

        JdtRenamePackage.FileChange appChange = result.changedFiles().stream()
                .filter(fc -> fc.oldPath().getFileName().toString().equals("App.java"))
                .findFirst().orElseThrow();

        assertThat(appChange.pathChanged()).as("App.java path should not change").isFalse();
        assertThat(appChange.newSource()).as("Single import must be updated").contains("import com.example.util.Calculator;");
        assertThat(appChange.newSource()).as("Wildcard import must be updated").contains("import com.example.util.*;");
    }

    @Test
    void rename_package_rejected_when_same_name() throws Exception {
        var project = new MavenProject(fixtures.projectPath("projects/rename-package"));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtRenamePackage.renamePackage(
                project, "com.example.service", "com.example.service"));
    }

    @Test
    void rename_package_updates_fqn_code_references() throws Exception {
        var project = new MavenProject(fixtures.projectPath("projects/rename-package-fqn"));
        JdtRenamePackage.Result result = JdtRenamePackage.renamePackage(
                project, "com.example.service", "com.example.util");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-package-fqn/src/main/java");

        Map<Path, String> outputs = new LinkedHashMap<>();
        result.changedFiles().stream()
                .sorted(Comparator.comparing(a -> a.newPath().getFileName().toString()))
                .forEach(fc -> outputs.put(fc.newPath(), fc.newSource()));

        Approvals.verify(
            RenameStoryBoard.titled("Rename package with FQN code references: com.example.service → com.example.util")
                .inputProject(inputs)
                .refactoring("rename package",
                        "`com.example.service` → `com.example.util`",
                        "FQN in field type, local variable, lambda body")
                .outputProject(outputs)
                .build()
        );
    }

    @Test
    void rename_package_updates_javadoc_fqn_references() throws Exception {
        var project = new MavenProject(fixtures.projectPath("projects/rename-package-javadoc"));
        JdtRenamePackage.Result result = JdtRenamePackage.renamePackage(
                project, "com.example.service", "com.example.util");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-package-javadoc/src/main/java");

        Map<Path, String> outputs = new LinkedHashMap<>();
        result.changedFiles().stream()
                .sorted(Comparator.comparing(a -> a.newPath().getFileName().toString()))
                .forEach(fc -> outputs.put(fc.newPath(), fc.newSource()));

        Approvals.verify(
            RenameStoryBoard.titled("Rename package: {@link} and @see FQN in Javadoc updated")
                .inputProject(inputs)
                .refactoring("rename package",
                        "`com.example.service` → `com.example.util`",
                        "{@link com.example.service.Calculator} and @see in App.java Javadoc must be updated")
                .outputProject(outputs)
                .build()
        );
    }

    // Unit test for remapPackage helper
    @Test
    void remapPackage_handles_direct_and_subpackage() {
        assertThat(JdtRenamePackage.remapPackage("com.example.service", "com.example.service", "com.example.util")).isEqualTo("com.example.util");
        assertThat(JdtRenamePackage.remapPackage("com.example.service.impl", "com.example.service", "com.example.util")).isEqualTo("com.example.util.impl");
        assertThat(JdtRenamePackage.remapPackage("com.example.other", "com.example.service", "com.example.util")).isNull();
        assertThat(JdtRenamePackage.remapPackage("com.example.serviceable", "com.example.service", "com.example.util")).isNull();
    }
}
