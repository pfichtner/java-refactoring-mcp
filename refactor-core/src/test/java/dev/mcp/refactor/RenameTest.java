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

/**
 * M3 approval tests: multi-file rename for fields, methods, and types,
 * plus single-file edge cases (shadowing).
 *
 * Each .approved.md shows: all input files → named refactoring → all changed output files.
 */
class RenameTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    // --- field ---

    @Test
    void rename_field_across_files() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/rename-field");
        MavenProject project = new MavenProject(projectRoot);

        Path personFile = project.sourceRoots().get(0).resolve("com/example/Person.java");
        String source = Files.readString(personFile);
        int offset = Fixtures.offsetOf(source, "public String name") + "public String ".length();

        Map<Path, String> changed = JdtRenamer.rename(project, personFile, offset, "fullName");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-field/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Rename field: name → fullName (multi-file)")
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

        Map<Path, String> changed = JdtRenamer.rename(project, calcFile, offset, "plus");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-method/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Rename method: add → plus (multi-file)")
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

        Map<Path, String> changed = JdtRenamer.rename(project, rectFile, offset, "Rect");

        Map<String, String> inputs = fixtures.loadProjectSources(
                "projects/rename-type/src/main/java");

        Approvals.verify(
            RenameStoryBoard.titled("Rename type: Rectangle → Rect (multi-file)")
                .inputProject(inputs)
                .refactoring("rename type", "`Rectangle` → `Rect`",
                        "target: " + Fixtures.lineCol(source, offset) + " in Rectangle.java")
                .outputProject(changed)
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
            RenameStoryBoard.titled("Rename local variable: x → local (field x must be untouched)")
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
            RenameStoryBoard.titled("Rename constructor directly — expect rejection")
                .javaSection("Input", source)
                .refactoring("rename constructor", "`Rectangle` → `Rect`",
                        "target: constructor declaration at " + Fixtures.lineCol(source, offset))
                .diagnostic(diagnostic)
                .build()
        );
    }
}
