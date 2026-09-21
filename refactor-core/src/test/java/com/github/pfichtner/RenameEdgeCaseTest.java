package com.github.pfichtner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RefactoringStoryBoard;

/**
 * M3 edge-case approval tests: overloaded methods, inheritance, generics,
 * lambdas/method references, anonymous classes.
 *
 * Each test documents the exact current behaviour — including known limitations.
 * If a limitation is fixed, the test will fail; inspect the diff and update the
 * approved file only if the new output is correct.
 */
class RenameEdgeCaseTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    // -------------------------------------------------------------------------
    // Overloaded methods
    // -------------------------------------------------------------------------

    @Test
    void rename_renames_only_matching_overload_not_siblings() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-overloaded");
        MavenProject project = new MavenProject(projectRoot);
        Path overloadedFile = project.sourceRoots().get(0).resolve("com/example/Overloaded.java");
        String source = Files.readString(overloadedFile);

        // Target: compute(int a, int b) — the 2-arg overload
        // "compute(int a, int b)" first occurrence (NOT the 3-arg version)
        int offset = Fixtures.offsetOf(source, "compute(int a, int b)");

        var changed = JdtRenamer.rename(project, overloadedFile, offset, "add");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-overloaded/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename method: compute(int,int) → add (sibling overload unchanged)")
                .inputProject(inputs)
                .refactoring("rename method", "`Overloaded.compute(int,int)` → `add`",
                        "only the 2-arg overload and its call sites are renamed; 3-arg compute unchanged")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Inheritance / override
    // -------------------------------------------------------------------------

    @Test
    void rename_base_method_does_not_follow_override_chain() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-inheritance");
        MavenProject project = new MavenProject(projectRoot);
        Path animalFile = project.sourceRoots().get(0).resolve("com/example/Animal.java");
        String source = Files.readString(animalFile);

        int offset = Fixtures.offsetOf(source, "void speak") + "void ".length();

        var changed = JdtRenamer.rename(project, animalFile, offset, "makeSound");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-inheritance/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename method: Animal.speak → makeSound (override chain limitation)")
                .inputProject(inputs)
                .refactoring("rename method", "`Animal.speak` → `makeSound`",
                        "KNOWN LIMITATION: Dog.speak() override is NOT renamed — "
                        + "the engine matches by binding key and does not follow virtual dispatch chains")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Generics
    // -------------------------------------------------------------------------

    @Test
    void rename_method_in_generic_class() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-generics");
        MavenProject project = new MavenProject(projectRoot);
        Path containerFile = project.sourceRoots().get(0).resolve("com/example/Container.java");
        String source = Files.readString(containerFile);

        int offset = Fixtures.offsetOf(source, "getValue") ;

        var changed = JdtRenamer.rename(project, containerFile, offset, "get");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-generics/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename method in generic class: Container<T>.getValue → get")
                .inputProject(inputs)
                .refactoring("rename method", "`Container<T>.getValue()` → `get()`",
                        "type parameter T and factory method preserved; return type unaffected")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Lambdas / method references
    // -------------------------------------------------------------------------

    @Test
    void rename_method_used_as_method_reference_in_lambda() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-lambda");
        MavenProject project = new MavenProject(projectRoot);
        Path calcFile = project.sourceRoots().get(0).resolve("com/example/Calculator.java");
        String source = Files.readString(calcFile);

        int offset = Fixtures.offsetOf(source, "square");

        var changed = JdtRenamer.rename(project, calcFile, offset, "squared");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-lambda/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename method referenced in lambda: Calculator.square → squared")
                .inputProject(inputs)
                .refactoring("rename method", "`Calculator.square` → `squared`",
                        "method reference this::square in run() must also be renamed")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Javadoc cross-references
    // -------------------------------------------------------------------------

    @Test
    void rename_type_updates_javadoc_link() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-javadoc-type");
        MavenProject project = new MavenProject(projectRoot);
        Path greeterFile = project.sourceRoots().get(0).resolve("com/example/Greeter.java");
        String source = Files.readString(greeterFile);

        int offset = Fixtures.offsetOf(source, "Greeter");

        var changed = JdtRenamer.rename(project, greeterFile, offset, "HelloService");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-javadoc-type/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename type: Greeter → HelloService ({@link} and @see updated)")
                .inputProject(inputs)
                .refactoring("rename type", "`Greeter` → `HelloService`",
                        "{@link Greeter} and @see Greeter in App.java Javadoc must be updated")
                .outputProject(changed)
                .filesystemSection(changed)
                .build()
        );
    }

    @Test
    void rename_method_updates_javadoc_link() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-javadoc-method");
        MavenProject project = new MavenProject(projectRoot);
        Path calcFile = project.sourceRoots().get(0).resolve("com/example/Calculator.java");
        String source = Files.readString(calcFile);

        int offset = Fixtures.offsetOf(source, "compute");

        var changed = JdtRenamer.rename(project, calcFile, offset, "add");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-javadoc-method/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename method: Calculator.compute → add ({@link} and @see updated)")
                .inputProject(inputs)
                .refactoring("rename method", "`Calculator.compute(int,int)` → `add`",
                        "{@link Calculator#compute} and @see Calculator#compute in App.java Javadoc must be updated")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Anonymous classes
    // -------------------------------------------------------------------------

    @Test
    void rename_interface_method_with_anonymous_class_implementation() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-anonymous");
        MavenProject project = new MavenProject(projectRoot);
        Path runnerFile = project.sourceRoots().get(0).resolve("com/example/Runner.java");
        String source = Files.readString(runnerFile);

        // Target: Task.execute() — the interface declaration
        int offset = Fixtures.offsetOf(source, "void execute") + "void ".length();

        var changed = JdtRenamer.rename(project, runnerFile, offset, "perform");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-anonymous/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename interface method: Task.execute → perform (anonymous impl)")
                .inputProject(inputs)
                .refactoring("rename method", "`Task.execute()` → `perform()`",
                        "interface declaration and call site renamed; "
                        + "KNOWN LIMITATION: anonymous class @Override not renamed "
                        + "(different binding key from the interface method)")
                .outputProject(changed)
                .build()
        );
    }
}
