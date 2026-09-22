package com.github.pfichtner.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

@CliFixture(root = "fixtures/projects/introduce-param-object/pom.xml")
class CliIntroduceParamObjectTest {

    @Test
    void dry_run_record_prints_preview_without_writing(CliTestBed bed) throws Exception {
        Path printerFile = bed.root().resolve("src/main/java/com/example/Printer.java");
        String before = Files.readString(printerFile);

        int exit = bed.cli().execute(
                "introduce-param-object",
                "--file", printerFile.toString(),
                "--line", "4",
                "--column", "17",
                "--params", "x,y",
                "--class-name", "Coordinate",
                "--record",
                "--dry-run");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(Files.readString(printerFile))
                .as("Dry-run must not modify file on disk").isEqualTo(before);

        Approvals.verify(bed.out().toString());
    }

    @Test
    void apply_record_writes_record_and_updates_call_sites(CliTestBed bed) throws Exception {
        Path srcRoot     = bed.root().resolve("src/main/java/com/example");
        Path printerFile = srcRoot.resolve("Printer.java");
        Path coordFile   = srcRoot.resolve("Coordinate.java");
        Path appFile     = srcRoot.resolve("App.java");

        int exit = bed.cli().execute(
                "introduce-param-object",
                "--file", printerFile.toString(),
                "--line", "4",
                "--column", "17",
                "--params", "x,y",
                "--class-name", "Coordinate",
                "--record");

        assertThat(exit).as("Expected exit code 0: " + bed.out()).isEqualTo(0);
        assertThat(coordFile).as("Coordinate.java must be created").exists();

        String snapshot = snapshot(
                "App.java",     Files.readString(appFile),
                "Coordinate.java", Files.readString(coordFile),
                "Printer.java", Files.readString(printerFile));
        Approvals.verify(snapshot);
    }

    private static String snapshot(String... nameAndContent) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nameAndContent.length; i += 2) {
            sb.append("=== ").append(nameAndContent[i]).append(" ===\n");
            sb.append(nameAndContent[i + 1].stripTrailing()).append("\n\n");
        }
        return sb.toString().stripTrailing();
    }
}
