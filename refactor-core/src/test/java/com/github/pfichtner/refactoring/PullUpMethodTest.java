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

        assertThat(changed.size()).isEqualTo(2);

        Path absAnimal = animalFile.toAbsolutePath().normalize();
        Path absDog    = dogFile.toAbsolutePath().normalize();
        assertThat(changed.containsKey(absAnimal)).as("superclass must be in result").isTrue();
        assertThat(changed.containsKey(absDog)).as("subclass must be in result").isTrue();

        Approvals.verify(
            RefactoringStoryBoard.titled("Pull up method: Dog.speak → Animal")
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

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtPullUpMethod.pullUp(project, animalFile, offset)).actual();
        assertThat(ex.getMessage()).contains("no explicit superclass");

        Approvals.verify(
            RefactoringStoryBoard.titled("Pull up method rejected: Animal has no superclass")
                .javaSection("Input: Animal.java", source)
                .refactoring("pull up method", "`Animal.name()`", Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void pull_up_handles_fqn_extends_clause() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/pull-up-method-fqn");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot  = project.sourceRoots().get(0);
        Path dogFile  = srcRoot.resolve("com/example/derived/Dog.java");
        Path animalFile = srcRoot.resolve("com/example/base/Animal.java");

        String dogSource    = Files.readString(dogFile);
        String animalSource = Files.readString(animalFile);

        int offset = Fixtures.offsetOf(dogSource, "speak");
        Map<Path, String> changed = JdtPullUpMethod.pullUp(project, dogFile, offset);

        assertThat(changed.size()).isEqualTo(2);

        Approvals.verify(
            RefactoringStoryBoard.titled("Pull up method: Dog.speak → Animal (FQN extends clause)")
                .inputProject(Map.of("Animal.java", animalSource, "Dog.java", dogSource))
                .refactoring("pull up method",
                        "`Dog.speak()` → `Animal`",
                        Fixtures.lineCol(dogSource, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void widen_visibility_changes_private_to_protected() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/pull-up-method-private");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot    = project.sourceRoots().get(0);
        Path dogFile    = srcRoot.resolve("com/example/Dog.java");
        Path animalFile = srcRoot.resolve("com/example/Animal.java");

        String dogSource    = Files.readString(dogFile);
        String animalSource = Files.readString(animalFile);

        int offset = Fixtures.offsetOf(dogSource, "bark");
        Map<Path, String> changed = JdtPullUpMethod.pullUp(project, dogFile, offset, true);

        assertThat(changed).hasSize(2);

        Approvals.verify(
            RefactoringStoryBoard.titled("Pull up method with widen_visibility: private Dog.bark → protected Animal.bark")
                .inputProject(Map.of("Animal.java", animalSource, "Dog.java", dogSource))
                .refactoring("pull up method",
                    "`Dog.bark()` → `Animal` (widen_visibility=true)",
                    Fixtures.lineCol(dogSource, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void no_widen_visibility_keeps_private_modifier() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/pull-up-method-private");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot    = project.sourceRoots().get(0);
        Path dogFile    = srcRoot.resolve("com/example/Dog.java");
        Path animalFile = srcRoot.resolve("com/example/Animal.java");

        String dogSource    = Files.readString(dogFile);
        String animalSource = Files.readString(animalFile);

        int offset = Fixtures.offsetOf(dogSource, "bark");
        Map<Path, String> changed = JdtPullUpMethod.pullUp(project, dogFile, offset, false);

        assertThat(changed).hasSize(2);

        Approvals.verify(
            RefactoringStoryBoard.titled("Pull up method without widen_visibility: private modifier preserved")
                .inputProject(Map.of("Animal.java", animalSource, "Dog.java", dogSource))
                .refactoring("pull up method",
                    "`Dog.bark()` → `Animal` (widen_visibility=false)",
                    Fixtures.lineCol(dogSource, offset))
                .outputProject(changed)
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

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtPullUpMethod.pullUp(project, dogFile, offset)).actual();
        assertThat(ex.getMessage()).contains("already declares");

        Approvals.verify(
            RefactoringStoryBoard.titled("Pull up method rejected: superclass already has name()")
                .javaSection("Input: Dog.java", source)
                .refactoring("pull up method", "`Dog.name()`", Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
