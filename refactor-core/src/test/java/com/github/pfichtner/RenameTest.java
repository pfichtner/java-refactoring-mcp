package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RefactoringStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Approval tests for rename refactorings: local variables, parameters,
 * fields, methods, and types (single-file and multi-file).
 *
 * Each .approved.md shows: input source(s) → named refactoring → output source(s).
 */
class RenameTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    // --- local variable ---

    @Test
    void rename_local_variable_from_declaration_site() throws Exception {
        String source = fixtures.load("rename/local-variable/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "int x") + "int ".length();
        String result = JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "answer");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename local variable: x → answer")
                .javaSection("Input", source)
                .refactoring("rename local variable", "`x` → `answer`",
                        "target: declaration site at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void rename_local_variable_from_reference_site() throws Exception {
        String source = fixtures.load("rename/local-variable/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "x * 7");
        String result = JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "answer");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename local variable: x → answer")
                .javaSection("Input", source)
                .refactoring("rename local variable", "`x` → `answer`",
                        "target: reference site at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void rename_y_does_not_affect_x() throws Exception {
        String source = fixtures.load("rename/local-variable/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "int y") + "int ".length();
        String result = JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "product");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename local variable: y → product (x must be untouched)")
                .javaSection("Input", source)
                .refactoring("rename local variable", "`y` → `product`",
                        "target: declaration site at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    // --- parameter ---

    @Test
    void rename_parameter_n_to_count() throws Exception {
        String source = fixtures.load("rename/parameter/input/Counter.java");
        int offset = Fixtures.offsetOf(source, "int n") + "int ".length();
        String result = JdtRenamer.renameLocalVariable(source, "Counter.java", offset, "count");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename parameter: n → count")
                .javaSection("Input", source)
                .refactoring("rename parameter", "`n` → `count`",
                        "target: declaration site at " + Fixtures.lineCol(source, offset))
                .javaSection("Output", result)
                .build()
        );
    }

    // --- precondition: non-variable rejected ---

    @Test
    void rename_non_variable_is_rejected() throws Exception {
        String source = fixtures.load("rename/local-variable/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "class Foo") + "class ".length();

        String diagnostic;
        try {
            JdtRenamer.renameLocalVariable(source, "Foo.java", offset, "Bar");
            diagnostic = "(no error — expected rejection)";
        } catch (IllegalArgumentException e) {
            diagnostic = e.getMessage();
        }

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename type name — expect rejection")
                .javaSection("Input", source)
                .refactoring("rename", "`Foo` → `Bar`",
                        "target: type name at " + Fixtures.lineCol(source, offset)
                        + " (not a local variable or parameter)")
                .diagnostic(diagnostic)
                .build()
        );
    }

    // --- field ---

    @Test
    void rename_field_across_files() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-field");
        MavenProject project = new MavenProject(projectRoot);

        Path personFile = project.sourceRoots().get(0).resolve("com/example/Person.java");
        String source = Files.readString(personFile);
        int offset = Fixtures.offsetOf(source, "public String name") + "public String ".length();

        var changed = JdtRenamer.rename(project, personFile, offset, "fullName");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-field/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename field: name → fullName (multi-file)")
                .inputProject(inputs)
                .refactoring("rename field", "`Person.name` → `Person.fullName`",
                        "target: " + Fixtures.lineCol(source, offset) + " in Person.java")
                .outputProject(changed)
                .build()
        );
    }

    // --- method ---

    @Test
    void rename_method_across_files() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-method");
        MavenProject project = new MavenProject(projectRoot);

        Path calcFile = project.sourceRoots().get(0).resolve("com/example/Calculator.java");
        String source = Files.readString(calcFile);
        int offset = Fixtures.offsetOf(source, "public int add") + "public int ".length();

        var changed = JdtRenamer.rename(project, calcFile, offset, "plus");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-method/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename method: add → plus (multi-file)")
                .inputProject(inputs)
                .refactoring("rename method", "`Calculator.add` → `Calculator.plus`",
                        "target: " + Fixtures.lineCol(source, offset) + " in Calculator.java")
                .outputProject(changed)
                .build()
        );
    }

    // --- type ---

    @Test
    void rename_type_across_files() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-type");
        MavenProject project = new MavenProject(projectRoot);

        Path rectFile = project.sourceRoots().get(0).resolve("com/example/Rectangle.java");
        String source = Files.readString(rectFile);
        int offset = Fixtures.offsetOf(source, "class Rectangle") + "class ".length();

        var changed = JdtRenamer.rename(project, rectFile, offset, "Rect");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-type/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename type: Rectangle → Rect (multi-file)")
                .inputProject(inputs)
                .refactoring("rename type", "`Rectangle` → `Rect`",
                        "target: " + Fixtures.lineCol(source, offset) + " in Rectangle.java")
                .outputProject(changed)
                .filesystemSection(changed)
                .build()
        );
    }

    // --- type via FQN reference ---

    @Test
    void rename_type_via_fqn_reference() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-type-fqn");
        MavenProject project = new MavenProject(projectRoot);

        Path rectFile = project.sourceRoots().get(0).resolve("com/example/service/Rectangle.java");
        String source = Files.readString(rectFile);
        int offset = Fixtures.offsetOf(source, "class Rectangle") + "class ".length();

        var changed = JdtRenamer.rename(project, rectFile, offset, "Rect");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-type-fqn/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename type via FQN reference: Rectangle → Rect")
                .inputProject(inputs)
                .refactoring("rename type",
                        "`com.example.service.Rectangle` → `com.example.service.Rect`",
                        "target: " + Fixtures.lineCol(source, offset) + " in Rectangle.java")
                .outputProject(changed)
                .filesystemSection(changed)
                .build()
        );
    }

    // --- shadowing ---

    @Test
    void rename_local_does_not_affect_shadowed_field() throws Exception {
        String source = fixtures.load("rename/local-variable/shadowing/input/Shadow.java");
        // Rename the LOCAL 'x' (not the field)
        int offset = Fixtures.offsetOf(source, "int x = 20") + "int ".length();
        String result = JdtRenamer.renameLocalVariable(source, "Shadow.java", offset, "local");

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename local variable: x → local (field x must be untouched)")
                .javaSection("Input", source)
                .refactoring("rename local variable", "`x` → `local`",
                        "target: " + Fixtures.lineCol(source, offset)
                        + " (local shadows field `this.x`)")
                .javaSection("Output", result)
                .build()
        );
    }

    // --- precondition: constructor target rejected ---

    @Test
    void rename_constructor_directly_is_rejected() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-type");
        MavenProject project = new MavenProject(projectRoot);

        Path rectFile = project.sourceRoots().get(0).resolve("com/example/Rectangle.java");
        String source = Files.readString(rectFile);
        // Offset of 'Rectangle' in the constructor declaration "public Rectangle("
        int offset = Fixtures.offsetOf(source, "public Rectangle(") + "public ".length();

        String diagnostic;
        try {
            JdtRenamer.rename(project, rectFile, offset, "Rect");
            diagnostic = "(no error — expected rejection)";
        } catch (IllegalArgumentException e) {
            diagnostic = e.getMessage();
        }

        Approvals.verify(
            RefactoringStoryBoard.titled("Rename constructor directly — expect rejection")
                .javaSection("Input", source)
                .refactoring("rename constructor", "`Rectangle` → `Rect`",
                        "target: constructor declaration at " + Fixtures.lineCol(source, offset))
                .diagnostic(diagnostic)
                .build()
        );
    }
}
