package com.github.pfichtner.refactoring.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code rename-package} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/rename-package/pom.xml")
class CliRenamePackageTest {

    @Test
    void apply_moves_package_directory(CliTestBed bed) throws Exception {
        Path movedFile = bed.root().resolve("src/main/java/com/example/util/Calculator.java");
        Path appFile = bed.root().resolve("src/main/java/com/example/app/App.java");

        int exit = bed.cli().execute(
                "rename-package",
                "--project", bed.root().toString(),
                "--old-package", "com.example.service",
                "--new-package", "com.example.util");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.exists(movedFile)).as("Class should move under the new package").isTrue();
        assertThat(Files.readString(appFile)).as("Import should be updated")
                .contains("import com.example.util.Calculator;");
    }
}
