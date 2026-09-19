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

class PullUpMethodTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void pull_up_moves_method_to_superclass() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/pull-up-method");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot  = project.sourceRoots().get(0);
        Path dogFile  = srcRoot.resolve("com/example/Dog.java");
        Path animalFile = srcRoot.resolve("com/example/Animal.java");

        String dogSource    = Files.readString(dogFile);
        String animalSource = Files.readString(animalFile);

        int offset = Fixtures.offsetOf(dogSource, "speak");
        Map<Path, String> changed = JdtPullUpMethod.pullUp(project, dogFile, offset);

        assertEquals(2, changed.size());

        Path absAnimal = animalFile.toAbsolutePath().normalize();
        Path absDog    = dogFile.toAbsolutePath().normalize();
        assertTrue(changed.containsKey(absAnimal), "superclass must be in result");
        assertTrue(changed.containsKey(absDog),    "subclass must be in result");

        Approvals.verify(
            RenameStoryBoard.titled("Pull up method: Dog.speak → Animal")
                .inputProject(Map.of("Animal.java", animalSource, "Dog.java", dogSource))
                .refactoring("pull up method",
                    "`Dog.speak()` → `Animal`",
                    Fixtures.lineCol(dogSource, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void reject_class_with_no_explicit_superclass() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/pull-up-method");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot    = project.sourceRoots().get(0);
        Path animalFile = srcRoot.resolve("com/example/Animal.java");
        String source   = Files.readString(animalFile);
        int offset      = Fixtures.offsetOf(source, "name");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdtPullUpMethod.pullUp(project, animalFile, offset));
        assertTrue(ex.getMessage().contains("no explicit superclass"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Pull up method rejected: Animal has no superclass")
                .javaSection("Input: Animal.java", source)
                .refactoring("pull up method", "`Animal.name()`", Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void reject_duplicate_method_in_superclass() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/pull-up-method");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot = project.sourceRoots().get(0);
        Path dogFile = srcRoot.resolve("com/example/Dog.java");
        String source = Files.readString(dogFile);
        // "name" exists in both Dog and Animal — should be rejected
        int offset = Fixtures.offsetOf(source, "name");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdtPullUpMethod.pullUp(project, dogFile, offset));
        assertTrue(ex.getMessage().contains("already declares"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Pull up method rejected: superclass already has name()")
                .javaSection("Input: Dog.java", source)
                .refactoring("pull up method", "`Dog.name()`", Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
