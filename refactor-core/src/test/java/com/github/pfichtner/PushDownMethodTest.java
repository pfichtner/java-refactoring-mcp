package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
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

    @Test
    void push_down_handles_fqn_extends_clause() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/push-down-method-fqn");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot    = project.sourceRoots().get(0);
        Path animalFile = srcRoot.resolve("com/example/base/Animal.java");
        Path dogFile    = srcRoot.resolve("com/example/derived/Dog.java");

        String animalSource = Files.readString(animalFile);
        String dogSource    = Files.readString(dogFile);

        int offset = Fixtures.offsetOf(animalSource, "speak");
        Map<Path, String> changed = JdtPushDownMethod.pushDown(project, animalFile, offset);

        assertEquals(2, changed.size());

        Approvals.verify(
            RenameStoryBoard.titled("Push down method: Animal.speak → Dog (FQN extends clause)")
                .inputProject(Map.of("Animal.java", animalSource, "Dog.java", dogSource))
                .refactoring("push down method",
                        "`Animal.speak()` → subclasses",
                        Fixtures.lineCol(animalSource, offset))
                .outputProject(changed)
                .build()
        );
    }
}
