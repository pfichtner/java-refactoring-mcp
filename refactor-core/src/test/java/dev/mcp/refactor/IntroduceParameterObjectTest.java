package dev.mcp.refactor;

import dev.mcp.refactor.project.MavenProject;
import dev.mcp.refactor.support.Fixtures;
import dev.mcp.refactor.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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

        // New class file created
        Path coordFile = printerFile.getParent().resolve("Coordinate.java");
        assertTrue(changed.containsKey(coordFile.toAbsolutePath().normalize()),
                "Coordinate.java must be created");

        // Method signature updated
        Path absPrinter = printerFile.toAbsolutePath().normalize();
        String newPrinter = changed.get(absPrinter);
        assertNotNull(newPrinter, "Printer.java must be in result");
        assertTrue(newPrinter.contains("Coordinate coordinate"),
                "method should have Coordinate parameter");
        assertFalse(newPrinter.contains("int x"), "grouped params removed from signature");
        assertTrue(newPrinter.contains("coordinate.getX()"), "body uses getter for x");
        assertTrue(newPrinter.contains("coordinate.getY()"), "body uses getter for y");

        // Call sites updated
        Path absApp   = appFile.toAbsolutePath().normalize();
        String newApp = changed.get(absApp);
        assertNotNull(newApp, "App.java must be in result");
        assertTrue(newApp.contains("new Coordinate(10, 20)"), "first call site wrapped");
        assertTrue(newApp.contains("new Coordinate(0, 0)"),   "second call site wrapped");

        Map<String, String> inputs = Map.of(
                "App.java", appSrc, "Printer.java", printerSrc);

        Approvals.verify(
            RenameStoryBoard.titled("Introduce parameter object: Printer.print(x,y) → Coordinate")
                .inputProject(inputs)
                .refactoring("introduce parameter object",
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

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdtIntroduceParameterObject.introduce(
                    project, printerFile, offset,
                    List.of("x"), "Coordinate", "coordinate"));
        assertTrue(ex.getMessage().contains("At least 2"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Introduce parameter object rejected: fewer than 2 params")
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

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdtIntroduceParameterObject.introduce(
                    project, printerFile, offset,
                    List.of("x", "unknown"), "Coordinate", "coordinate"));
        assertTrue(ex.getMessage().contains("not found"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Introduce parameter object rejected: unknown param name")
                .javaSection("Input: Printer.java", source)
                .refactoring("introduce parameter object",
                    "`x, unknown` (unknown does not exist)",
                    Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
