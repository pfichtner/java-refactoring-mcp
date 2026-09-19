package dev.mcp.refactor;

import dev.mcp.refactor.project.MavenProject;
import dev.mcp.refactor.support.Fixtures;
import dev.mcp.refactor.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Approval tests for Move Class.
 */
class MoveClassTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void move_class_updates_package_and_imports() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-class");
        MavenProject project = new MavenProject(projectRoot);

        Path calcFile = project.sourceRoots().get(0)
                .resolve("com/example/service/Calculator.java");
        String calcSource = Files.readString(calcFile);
        String appSource  = Files.readString(
                project.sourceRoots().get(0).resolve("com/example/app/App.java"));

        JdtMoveClass.Result result = JdtMoveClass.moveClass(
                project, calcFile, "com.example.util");

        // Verify new path
        assertTrue(result.newFilePath().toString().endsWith("com/example/util/Calculator.java"),
                "New path: " + result.newFilePath());

        // Verify App.java import was updated
        assertEquals(1, result.changedImports().size());
        String updatedApp = result.changedImports().values().iterator().next();

        // Storyboard approval
        Approvals.verify(
            RenameStoryBoard.titled("Move class: Calculator → com.example.util")
                .javaSection("Input: Calculator.java", calcSource)
                .javaSection("Input: App.java", appSource)
                .refactoring("move class",
                        "`com.example.service.Calculator` → `com.example.util.Calculator`",
                        "new path: " + result.newFilePath().getFileName()
                        + " (original file must be deleted by caller)")
                .javaSection("Output: Calculator.java (new location)", result.newClassSource())
                .javaSection("Output: App.java (updated import)", updatedApp)
                .build()
        );
    }

    @Test
    void move_class_rejected_when_already_in_target_package() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-class");
        MavenProject project = new MavenProject(projectRoot);
        Path calcFile = project.sourceRoots().get(0)
                .resolve("com/example/service/Calculator.java");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtMoveClass.moveClass(project, calcFile, "com.example.service"));
        assertTrue(ex.getMessage().contains("already in package"), ex.getMessage());
    }
}
