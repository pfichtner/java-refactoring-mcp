package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.approvaltests.core.Options;
import org.approvaltests.core.Scrubber;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for the {@code rename-package} CLI subcommand.
 */
@CliFixture(root = "fixtures/projects/rename-package/pom.xml")
class CliRenamePackageTest {

    @Test
    void dry_run_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path calculatorFile = bed.root().resolve("src/main/java/com/example/service/Calculator.java");
        String before = Files.readString(calculatorFile);

        int exit = bed.cli().execute(
                "rename-package",
                "--project", bed.root().toString(),
                "--old-package", "com.example.service",
                "--new-package", "com.example.util",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(calculatorFile)).as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(bed.out().toString(), new Options().withScrubber(scrubRoot(bed)));
    }

    private static Scrubber scrubRoot(CliTestBed bed) {
        String root = bed.root().toString() + java.io.File.separator;
        return input -> input.replace(root, "{ROOT}/");
    }

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
