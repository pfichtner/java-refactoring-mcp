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

class PullUpFieldTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void pull_up_moves_field_to_superclass() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/pull-up-field");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot    = project.sourceRoots().get(0);
        Path dogFile    = srcRoot.resolve("com/example/Dog.java");
        Path animalFile = srcRoot.resolve("com/example/Animal.java");

        String dogSource    = Files.readString(dogFile);
        String animalSource = Files.readString(animalFile);

        int offset = Fixtures.offsetOf(dogSource, "breed");
        Map<Path, String> changed = JdtPullUpField.pullUp(project, dogFile, offset);

        assertEquals(2, changed.size());

        Path absAnimal = animalFile.toAbsolutePath().normalize();
        Path absDog    = dogFile.toAbsolutePath().normalize();
        assertTrue(changed.containsKey(absAnimal), "superclass must be in result");
        assertTrue(changed.containsKey(absDog),    "subclass must be in result");

        Approvals.verify(
            RenameStoryBoard.titled("Pull up field: Dog.breed → Animal")
                .inputProject(Map.of("Animal.java", animalSource, "Dog.java", dogSource))
                .refactoring("pull up field",
                    "`Dog.breed` → `Animal`",
                    Fixtures.lineCol(dogSource, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void reject_class_with_no_explicit_superclass() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/pull-up-field");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot    = project.sourceRoots().get(0);
        Path animalFile = srcRoot.resolve("com/example/Animal.java");
        String source   = Files.readString(animalFile);
        int offset      = Fixtures.offsetOf(source, "name");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdtPullUpField.pullUp(project, animalFile, offset));
        assertTrue(ex.getMessage().contains("no explicit superclass"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Pull up field rejected: Animal has no superclass")
                .javaSection("Input: Animal.java", source)
                .refactoring("pull up field", "`Animal.name`", Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void reject_duplicate_field_in_superclass() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/pull-up-field");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot = project.sourceRoots().get(0);
        Path dogFile = srcRoot.resolve("com/example/Dog.java");
        String source = Files.readString(dogFile);
        // "name" exists in both Dog and Animal — should be rejected
        int offset = Fixtures.offsetOf(source, "name");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdtPullUpField.pullUp(project, dogFile, offset));
        assertTrue(ex.getMessage().contains("already declares"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Pull up field rejected: superclass already has name")
                .javaSection("Input: Dog.java", source)
                .refactoring("pull up field", "`Dog.name`", Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
