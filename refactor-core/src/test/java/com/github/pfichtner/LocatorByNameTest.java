package com.github.pfichtner;

import com.github.pfichtner.locator.Locator;
import com.github.pfichtner.locator.LocatorResolver;
import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Approval tests verifying that name-based locators produce identical
 * refactoring results to equivalent position-based locators.
 *
 * Each test uses the same fixture project as its position-based counterpart
 * in {@link RenameTest} / pull-up/push-down tests, but identifies the target
 * element by name rather than line/column.
 */
class LocatorByNameTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    // -------------------------------------------------------------------------
    // rename method by name
    // -------------------------------------------------------------------------

    @Test
    void rename_method_by_name() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-method");
        MavenProject project = new MavenProject(projectRoot);

        Path calcFile = project.sourceRoots().get(0).resolve("com/example/Calculator.java");
        String source = Files.readString(calcFile);
        int offset = LocatorResolver.resolve(new Locator.MethodName("add"), source, "Calculator.java");

        Map<Path, String> changed = JdtRenamer.rename(project, calcFile, offset, "plus");
        Map<String, String> inputs = fixtures.loadProjectSources("projects/rename-method/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Rename method by name: add → plus")
                .inputProject(inputs)
                .refactoring("rename method", "`Calculator.add` → `Calculator.plus`",
                        "target: method 'add'")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // rename field by name
    // -------------------------------------------------------------------------

    @Test
    void rename_field_by_name() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-field");
        MavenProject project = new MavenProject(projectRoot);

        Path personFile = project.sourceRoots().get(0).resolve("com/example/Person.java");
        String source = Files.readString(personFile);
        int offset = LocatorResolver.resolve(new Locator.FieldName("name"), source, "Person.java");

        Map<Path, String> changed = JdtRenamer.rename(project, personFile, offset, "fullName");
        Map<String, String> inputs = fixtures.loadProjectSources("projects/rename-field/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Rename field by name: name → fullName")
                .inputProject(inputs)
                .refactoring("rename field", "`Person.name` → `Person.fullName`",
                        "target: field 'name'")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // rename type by name
    // -------------------------------------------------------------------------

    @Test
    void rename_type_by_name() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-type");
        MavenProject project = new MavenProject(projectRoot);

        Path rectFile = project.sourceRoots().get(0).resolve("com/example/Rectangle.java");
        String source = Files.readString(rectFile);
        int offset = LocatorResolver.resolve(new Locator.TypeName("Rectangle"), source, "Rectangle.java");

        Map<Path, String> changed = JdtRenamer.rename(project, rectFile, offset, "Rect");
        Map<String, String> inputs = fixtures.loadProjectSources("projects/rename-type/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Rename type by name: Rectangle → Rect")
                .inputProject(inputs)
                .refactoring("rename type", "`Rectangle` → `Rect`",
                        "target: type 'Rectangle'")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // pull up method by name
    // -------------------------------------------------------------------------

    @Test
    void pull_up_method_by_name() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/pull-up-method");
        MavenProject project = new MavenProject(projectRoot);

        Path dogFile = project.sourceRoots().get(0).resolve("com/example/Dog.java");
        String source = Files.readString(dogFile);
        int offset = LocatorResolver.resolve(new Locator.MethodName("speak"), source, "Dog.java");

        Map<Path, String> changed = JdtPullUpMethod.pullUp(project, dogFile, offset);
        Map<String, String> inputs = fixtures.loadProjectSources("projects/pull-up-method/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Pull up method by name: Dog.speak")
                .inputProject(inputs)
                .refactoring("pull up method", "`Dog.speak()`",
                        "target: method 'speak'")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // pull up field by name
    // -------------------------------------------------------------------------

    @Test
    void pull_up_field_by_name() throws Exception {
        // Dog.java has: protected String name; protected String breed;
        Path projectRoot = fixtures.projectPath("projects/pull-up-field");
        MavenProject project = new MavenProject(projectRoot);

        Path dogFile = project.sourceRoots().get(0).resolve("com/example/Dog.java");
        String source = Files.readString(dogFile);
        int offset = LocatorResolver.resolve(new Locator.FieldName("breed"), source, "Dog.java");

        Map<Path, String> changed = JdtPullUpField.pullUp(project, dogFile, offset);
        Map<String, String> inputs = fixtures.loadProjectSources("projects/pull-up-field/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Pull up field by name: Dog.breed")
                .inputProject(inputs)
                .refactoring("pull up field", "`Dog.breed`",
                        "target: field 'breed'")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // remove param by name (ParameterInMethod locator)
    // -------------------------------------------------------------------------

    @Test
    void remove_param_by_name() throws Exception {
        // Computation.java has: public int add(int a, int b, int c) { return a + b; }
        // 'c' is unused
        Path projectRoot = fixtures.projectPath("projects/remove-param");
        MavenProject project = new MavenProject(projectRoot);

        Path compFile = project.sourceRoots().get(0).resolve("com/example/Computation.java");
        String source = Files.readString(compFile);
        int offset = LocatorResolver.resolve(
                new Locator.ParameterInMethod("add", "c"), source, "Computation.java");

        Map<Path, String> changed = JdtRemoveParam.removeParam(project, compFile, offset);
        Map<String, String> inputs = fixtures.loadProjectSources("projects/remove-param/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Remove param by name: add.c")
                .inputProject(inputs)
                .refactoring("remove param", "`c` from `add`",
                        "target: parameter 'c' in method 'add'")
                .outputProject(changed)
                .build()
        );
    }
}
