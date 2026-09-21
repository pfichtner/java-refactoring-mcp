package com.github.pfichtner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RefactoringStoryBoard;

class IntroduceParameterObjectTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void introduce_parameter_object_creates_class_and_updates_call_sites() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/introduce-param-object");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot     = project.sourceRoots().get(0);
        Path printerFile = srcRoot.resolve("com/example/Printer.java");
        Path appFile     = srcRoot.resolve("com/example/App.java");

        String printerSrc = Files.readString(printerFile);
        String appSrc     = Files.readString(appFile);

        // Offset inside the print method declaration
        int offset = Fixtures.offsetOf(printerSrc, "print");

        Map<Path, String> changed = JdtIntroduceParameterObject.introduce(
                project, printerFile, offset,
                List.of("x", "y"), "Coordinate", "coordinate");

        Path coordFile = printerFile.getParent().resolve("Coordinate.java");
        assertThat(changed.containsKey(coordFile.toAbsolutePath().normalize())).as("Coordinate.java must be created").isTrue();
        assertThat(changed.containsKey(printerFile.toAbsolutePath().normalize())).as("Printer.java must be in result").isTrue();
        assertThat(changed.containsKey(appFile.toAbsolutePath().normalize())).as("App.java must be in result").isTrue();

        Map<String, String> inputs = Map.of(
                "App.java", appSrc, "Printer.java", printerSrc);

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce parameter object: Printer.print(x,y) → Coordinate")
                .inputProject(inputs)
                .refactoring("introduce parameter object",
                    "`int x, int y` → `Coordinate coordinate`",
                    Fixtures.lineCol(printerSrc, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void introduce_parameter_object_as_record() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/introduce-param-object");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot     = project.sourceRoots().get(0);
        Path printerFile = srcRoot.resolve("com/example/Printer.java");
        Path appFile     = srcRoot.resolve("com/example/App.java");

        String printerSrc = Files.readString(printerFile);
        String appSrc     = Files.readString(appFile);

        int offset = Fixtures.offsetOf(printerSrc, "print");

        Map<Path, String> changed = JdtIntroduceParameterObject.introduce(
                project, printerFile, offset,
                List.of("x", "y"), "Coordinate", "coordinate", true);

        Path coordFile = printerFile.getParent().resolve("Coordinate.java");
        assertThat(changed.containsKey(coordFile.toAbsolutePath().normalize())).as("Coordinate.java must be created").isTrue();
        assertThat(changed.containsKey(printerFile.toAbsolutePath().normalize())).as("Printer.java must be in result").isTrue();
        assertThat(changed.containsKey(appFile.toAbsolutePath().normalize())).as("App.java must be in result").isTrue();

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce parameter object as record: Printer.print(x,y) → Coordinate")
                .inputProject(Map.of("App.java", appSrc, "Printer.java", printerSrc))
                .refactoring("introduce parameter object (--record)",
                    "`int x, int y` → `Coordinate coordinate`",
                    Fixtures.lineCol(printerSrc, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void introduce_parameter_object_rejected_when_fewer_than_two_params() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/introduce-param-object");
        MavenProject project = new MavenProject(projectRoot);
        Path printerFile = project.sourceRoots().get(0).resolve("com/example/Printer.java");
        String source    = Files.readString(printerFile);
        int offset       = Fixtures.offsetOf(source, "print");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtIntroduceParameterObject.introduce(
                project, printerFile, offset,
                List.of("x"), "Coordinate", "coordinate")).actual();
        assertThat(ex.getMessage()).contains("At least 2");

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce parameter object rejected: fewer than 2 params")
                .javaSection("Input: Printer.java", source)
                .refactoring("introduce parameter object",
                    "only `x` specified",
                    Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void introduce_parameter_object_rejected_when_param_name_not_found() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/introduce-param-object");
        MavenProject project = new MavenProject(projectRoot);
        Path printerFile = project.sourceRoots().get(0).resolve("com/example/Printer.java");
        String source    = Files.readString(printerFile);
        int offset       = Fixtures.offsetOf(source, "print");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtIntroduceParameterObject.introduce(
                project, printerFile, offset,
                List.of("x", "unknown"), "Coordinate", "coordinate")).actual();
        assertThat(ex.getMessage()).contains("not found");

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce parameter object rejected: unknown param name")
                .javaSection("Input: Printer.java", source)
                .refactoring("introduce parameter object",
                    "`x, unknown` (unknown does not exist)",
                    Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
