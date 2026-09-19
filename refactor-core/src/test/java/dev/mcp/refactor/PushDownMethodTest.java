package dev.mcp.refactor;

import dev.mcp.refactor.project.MavenProject;
import dev.mcp.refactor.support.Fixtures;
import dev.mcp.refactor.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PushDownMethodTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void push_down_copies_method_to_all_subclasses() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/push-down-method");
        MavenProject project  = new MavenProject(projectRoot);
        Path srcRoot    = project.sourceRoots().get(0);
        Path shapeFile  = srcRoot.resolve("com/example/Shape.java");
        Path circleFile = srcRoot.resolve("com/example/Circle.java");
        Path rectFile   = srcRoot.resolve("com/example/Rectangle.java");

        String shapeSource  = Files.readString(shapeFile);
        String circleSource = Files.readString(circleFile);
        String rectSource   = Files.readString(rectFile);

        int offset = Fixtures.offsetOf(shapeSource, "area");
        Map<Path, String> changed = JdtPushDownMethod.pushDown(project, shapeFile, offset);

        assertEquals(3, changed.size());

        Path absShape  = shapeFile.toAbsolutePath().normalize();
        Path absCircle = circleFile.toAbsolutePath().normalize();
        Path absRect   = rectFile.toAbsolutePath().normalize();

        Approvals.verify(
            RenameStoryBoard.titled("Push down method: Shape.area → Circle, Rectangle")
                .inputProject(Map.of(
                    "Circle.java", circleSource,
                    "Rectangle.java", rectSource,
                    "Shape.java", shapeSource))
                .refactoring("push down method",
                    "`Shape.area()` → direct subclasses",
                    Fixtures.lineCol(shapeSource, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void reject_no_subclasses_in_project() throws Exception {
        // Dog has no subclasses in the pull-up-method fixture
        Path projectRoot = fixtures.projectPath("projects/pull-up-method");
        MavenProject project = new MavenProject(projectRoot);
        Path dogFile = project.sourceRoots().get(0).resolve("com/example/Dog.java");
        String dogSrc = Files.readString(dogFile);
        int offset    = Fixtures.offsetOf(dogSrc, "speak");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdtPushDownMethod.pushDown(project, dogFile, offset));
        assertTrue(ex.getMessage().contains("No direct subclasses"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Push down method rejected: Dog has no subclasses")
                .javaSection("Input: Dog.java", dogSrc)
                .refactoring("push down method", "`Dog.speak()`", Fixtures.lineCol(dogSrc, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
