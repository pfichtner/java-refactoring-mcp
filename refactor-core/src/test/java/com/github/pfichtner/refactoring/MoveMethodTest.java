package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.project.MavenProject;
import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

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
        Map<Path, String> changed = JdtMoveMethod.moveMethod(project, printerFile, offset, "com.example.Report");

        assertThat(changed).hasSize(2);
        assertThat(changed).containsKey(reportFile.toAbsolutePath().normalize());
        assertThat(changed).containsKey(printerFile.toAbsolutePath().normalize());

        Approvals.verify(
            RefactoringStoryBoard.titled("Move method: Printer.format → com.example.Report")
                .inputProject(Map.of("Printer.java", printerSource, "Report.java", reportSource))
                .refactoring("move method", "`Printer.format(Report)` → `com.example.Report`",
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
        Map<Path, String> changed = JdtMoveMethod.moveMethod(project, printerFile, offset, "com.example.Report");

        assertThat(changed).hasSize(2);
        assertThat(changed).containsKey(reportFile.toAbsolutePath().normalize());
        assertThat(changed).containsKey(printerFile.toAbsolutePath().normalize());

        Approvals.verify(
            RefactoringStoryBoard.titled("Move method to named target: Printer.byline(Report, Author) → com.example.Report")
                .inputProject(Map.of("Printer.java", printerSource, "Report.java", reportSource))
                .refactoring("move method", "`Printer.byline(Report, Author)` → `com.example.Report` (not Author)",
                        "target class named explicitly by FQN; " + Fixtures.lineCol(printerSource, offset) + " in Printer.java")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void disambiguates_target_by_fully_qualified_name() throws Exception {
        // Both com.example.Report and com.other.Report exist in the fixture project.
        // The FQN must select the correct one — not just any file named Report.java.
        Path projectRoot = fixtures.projectPath("projects/move-method");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot = project.sourceRoots().get(0);
        Path printerFile       = srcRoot.resolve("com/example/Printer.java");
        Path exampleReportFile = srcRoot.resolve("com/example/Report.java");
        Path otherReportFile   = srcRoot.resolve("com/other/Report.java");

        String printerSource   = Files.readString(printerFile);
        String exampleReportSrc = Files.readString(exampleReportFile);
        String otherReportSrc  = Files.readString(otherReportFile);

        int offset = Fixtures.offsetOf(printerSource, "public String byline") + "public String ".length();
        Map<Path, String> changed = JdtMoveMethod.moveMethod(project, printerFile, offset, "com.example.Report");

        assertThat(changed).hasSize(2);
        assertThat(changed).containsKey(exampleReportFile.toAbsolutePath().normalize());
        assertThat(changed).containsKey(printerFile.toAbsolutePath().normalize());
        assertThat(changed).doesNotContainKey(otherReportFile.toAbsolutePath().normalize());

        Approvals.verify(
            RefactoringStoryBoard.titled("Move method: FQN selects com.example.Report over com.other.Report")
                .inputProject(Map.of(
                        "Printer.java", printerSource,
                        "Report.java (com.example)", exampleReportSrc,
                        "Report.java (com.other)", otherReportSrc))
                .refactoring("move method", "`Printer.byline(Report, Author)` → `com.example.Report`",
                        "target: " + Fixtures.lineCol(printerSource, offset) + " in Printer.java")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void widen_visibility_changes_private_to_package_private_same_package() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-method-private");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot     = project.sourceRoots().get(0);
        Path printerFile = srcRoot.resolve("com/example/Printer.java");
        Path reportFile  = srcRoot.resolve("com/example/Report.java");

        String printerSource = Files.readString(printerFile);
        String reportSource  = Files.readString(reportFile);

        int offset = Fixtures.offsetOf(printerSource, "format");
        Map<Path, String> changed = JdtMoveMethod.moveMethod(project, printerFile, offset, "com.example.Report", true);

        assertThat(changed).hasSize(2);

        Approvals.verify(
            RefactoringStoryBoard.titled("Move method with widen_visibility (same package): private → package-private")
                .inputProject(Map.of("Printer.java", printerSource, "Report.java", reportSource))
                .refactoring("move method",
                    "`Printer.format(Report)` → `com.example.Report` (widen_visibility=true, same package → package-private)",
                    Fixtures.lineCol(printerSource, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void no_widen_visibility_keeps_private_modifier() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-method-private");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot     = project.sourceRoots().get(0);
        Path printerFile = srcRoot.resolve("com/example/Printer.java");
        Path reportFile  = srcRoot.resolve("com/example/Report.java");

        String printerSource = Files.readString(printerFile);
        String reportSource  = Files.readString(reportFile);

        int offset = Fixtures.offsetOf(printerSource, "format");
        Map<Path, String> changed = JdtMoveMethod.moveMethod(project, printerFile, offset, "com.example.Report", false);

        assertThat(changed).hasSize(2);

        Approvals.verify(
            RefactoringStoryBoard.titled("Move method without widen_visibility: private modifier preserved")
                .inputProject(Map.of("Printer.java", printerSource, "Report.java", reportSource))
                .refactoring("move method",
                    "`Printer.format(Report)` → `com.example.Report` (widen_visibility=false)",
                    Fixtures.lineCol(printerSource, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void reject_when_target_in_other_package_already_has_same_method() throws Exception {
        // com.other.Report already declares format() — moving Printer.format to it must be rejected.
        Path projectRoot = fixtures.projectPath("projects/move-method");
        MavenProject project = new MavenProject(projectRoot);
        Path printerFile = project.sourceRoots().get(0).resolve("com/example/Printer.java");
        String source = Files.readString(printerFile);

        int offset = Fixtures.offsetOf(source, "public String format") + "public String ".length();

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtMoveMethod.moveMethod(project, printerFile, offset, "com.other.Report"))
                .actual();
        assertThat(ex.getMessage()).contains("already declares");

        Approvals.verify(
            RefactoringStoryBoard.titled("Move method rejected: com.other.Report already declares format")
                .javaSection("Input: Printer.java", source)
                .refactoring("move method", "`Printer.format(Report)` → `com.other.Report`",
                        Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
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
                .isThrownBy(() -> JdtMoveMethod.moveMethod(project, printerFile, offset, "com.example.NonExistent"))
                .actual();
        assertThat(ex.getMessage()).contains("not found");

        Approvals.verify(
            RefactoringStoryBoard.titled("Move method rejected: target class not found")
                .javaSection("Input: Printer.java", source)
                .refactoring("move method", "`Printer.format(Report)` → `com.example.NonExistent`",
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
                .isThrownBy(() -> JdtMoveMethod.moveMethod(project, printerFile, offset, "com.example.Report"))
                .actual();
        assertThat(ex.getMessage()).contains("already declares");

        Approvals.verify(
            RefactoringStoryBoard.titled("Move method rejected: Report already declares describe(Report)")
                .javaSection("Input: Printer.java", source)
                .refactoring("move method", "`Printer.describe(Report)` → `com.example.Report`",
                        Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
