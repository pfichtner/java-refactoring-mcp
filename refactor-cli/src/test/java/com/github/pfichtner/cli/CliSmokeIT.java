package com.github.pfichtner.cli;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: launches the CLI fat-jar as a subprocess and runs a rename
 * dry-run against the rename-method fixture project.
 */
class CliSmokeIT {

    @Test
    void cli_rename_dry_run_exits_zero_and_shows_new_name() throws Exception {
        Path fatJar = Path.of(System.getProperty("fat.jar"));
        Path calcFile = Path.of(
                CliSmokeIT.class.getClassLoader()
                        .getResource("fixtures/projects/rename-method/src/main/java/com/example/Calculator.java")
                        .toURI());

        Process proc = new ProcessBuilder(
                "java", "-jar", fatJar.toString(),
                "rename",
                "--file", calcFile.toString(),
                "--line", "4", "--column", "16",
                "--name", "plus",
                "--dry-run"
        ).redirectErrorStream(true).start();

        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = proc.waitFor(30, TimeUnit.SECONDS);

        assertThat(finished).as("process timed out").isTrue();
        assertThat(proc.exitValue()).as("exit code").isEqualTo(0);
        assertThat(output).contains("plus");
    }
}
