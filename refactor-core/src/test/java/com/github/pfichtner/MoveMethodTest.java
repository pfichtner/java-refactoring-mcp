package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MoveMethodTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void move_method_moves_to_target_class() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-method");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot = project.sourceRoots().get(0);
        Path printerFile = srcRoot.resolve("com/example/Printer.java");
        Path reportFile  = srcRoot.resolve("com/example/Report.java");

        String printerSource = Files.readString(printerFile);
        String reportSource  = Files.readString(reportFile);

        int offset = Fixtures.offsetOf(printerSource, "public String format") + "public String ".length();
        Map<Path, String> changed = JdtMoveMethod.moveMethod(project, printerFile, offset, "Report");

        assertThat(changed).hasSize(2);
        assertThat(changed).containsKey(reportFile.toAbsolutePath().normalize());
        assertThat(changed).containsKey(printerFile.toAbsolutePath().normalize());

        Approvals.verify(
            RenameStoryBoard.titled("Move method: Printer.format → Report")
                .inputProject(Map.of("Printer.java", printerSource, "Report.java", reportSource))
                .refactoring("move method", "`Printer.format(Report)` → `Report`",
                        "target: " + Fixtures.lineCol(printerSource, offset) + " in Printer.java")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void move_method_with_multiple_parameter_types_to_named_target() throws Exception {
        // byline(Report, Author) has two candidate parameter types.
        // The caller names the target explicitly; the API does not guess.
        Path projectRoot = fixtures.projectPath("projects/move-method");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot = project.sourceRoots().get(0);
        Path printerFile = srcRoot.resolve("com/example/Printer.java");
        Path reportFile  = srcRoot.resolve("com/example/Report.java");

        String printerSource = Files.readString(printerFile);
        String reportSource  = Files.readString(reportFile);

        int offset = Fixtures.offsetOf(printerSource, "public String byline") + "public String ".length();
        Map<Path, String> changed = JdtMoveMethod.moveMethod(project, printerFile, offset, "Report");

        assertThat(changed).hasSize(2);
        assertThat(changed).containsKey(reportFile.toAbsolutePath().normalize());
        assertThat(changed).containsKey(printerFile.toAbsolutePath().normalize());

        Approvals.verify(
            RenameStoryBoard.titled("Move method to named target: Printer.byline(Report, Author) → Report")
                .inputProject(Map.of("Printer.java", printerSource, "Report.java", reportSource))
                .refactoring("move method", "`Printer.byline(Report, Author)` → `Report` (not Author)",
                        "target class named explicitly; " + Fixtures.lineCol(printerSource, offset) + " in Printer.java")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void reject_target_class_not_found() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-method");
        MavenProject project = new MavenProject(projectRoot);
        Path printerFile = project.sourceRoots().get(0).resolve("com/example/Printer.java");
        String source = Files.readString(printerFile);

        int offset = Fixtures.offsetOf(source, "public String format") + "public String ".length();

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtMoveMethod.moveMethod(project, printerFile, offset, "NonExistent"))
                .actual();
        assertThat(ex.getMessage()).contains("not found");

        Approvals.verify(
            RenameStoryBoard.titled("Move method rejected: target class not found")
                .javaSection("Input: Printer.java", source)
                .refactoring("move method", "`Printer.format(Report)` → `NonExistent`",
                        Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void reject_duplicate_method_in_target() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-method");
        MavenProject project = new MavenProject(projectRoot);
        Path printerFile = project.sourceRoots().get(0).resolve("com/example/Printer.java");
        String source = Files.readString(printerFile);

        // "describe" exists in both Printer and Report — must be rejected
        int offset = Fixtures.offsetOf(source, "public String describe") + "public String ".length();

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtMoveMethod.moveMethod(project, printerFile, offset, "Report"))
                .actual();
        assertThat(ex.getMessage()).contains("already declares");

        Approvals.verify(
            RenameStoryBoard.titled("Move method rejected: Report already declares describe(Report)")
                .javaSection("Input: Printer.java", source)
                .refactoring("move method", "`Printer.describe(Report)` → `Report`",
                        Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
