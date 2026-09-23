package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Files;
import java.nio.file.Path;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.project.MavenProject;
import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

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
        assertThat(result.newFilePath().toString().endsWith("com/example/util/Calculator.java")).as("New path: " + result.newFilePath()).isTrue();

        // Verify App.java import was updated
        assertThat(result.changedImports().size()).isEqualTo(1);
        String updatedApp = result.changedImports().values().iterator().next();

        // Storyboard approval
        Approvals.verify(
            RefactoringStoryBoard.titled("Move class: Calculator → com.example.util")
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
    void move_class_updates_fqn_code_references() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-class-fqn");
        MavenProject project = new MavenProject(projectRoot);

        Path calcFile = project.sourceRoots().get(0)
                .resolve("com/example/service/Calculator.java");
        String calcSource = Files.readString(calcFile);
        String appSource  = Files.readString(
                project.sourceRoots().get(0).resolve("com/example/app/App.java"));

        JdtMoveClass.Result result = JdtMoveClass.moveClass(
                project, calcFile, "com.example.util");

        assertThat(result.changedImports().size()).isEqualTo(1);
        String updatedApp = result.changedImports().values().iterator().next();

        Approvals.verify(
            RefactoringStoryBoard.titled("Move class: Calculator → com.example.util (FQN code references)")
                .javaSection("Input: Calculator.java", calcSource)
                .javaSection("Input: App.java", appSource)
                .refactoring("move class",
                        "`com.example.service.Calculator` → `com.example.util.Calculator`",
                        "new path: " + result.newFilePath().getFileName()
                        + " (original file must be deleted by caller)")
                .javaSection("Output: Calculator.java (new location)", result.newClassSource())
                .javaSection("Output: App.java (updated FQN references)", updatedApp)
                .build()
        );
    }

    @Test
    void widen_visibility_makes_package_private_class_public() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-class-pkg-private");
        MavenProject project = new MavenProject(projectRoot);
        Path utilFile = project.sourceRoots().get(0).resolve("com/example/Utils.java");
        String utilSource = Files.readString(utilFile);

        JdtMoveClass.Result result = JdtMoveClass.moveClass(project, utilFile, "com.util", true);

        Approvals.verify(
            RefactoringStoryBoard.titled("Move class with widen_visibility: package-private → public")
                .javaSection("Input: Utils.java (package-private)", utilSource)
                .refactoring("move class",
                    "`com.example.Utils` → `com.util.Utils` (widen_visibility=true)",
                    "new path: " + result.newFilePath().getFileName())
                .javaSection("Output: Utils.java (new location)", result.newClassSource())
                .build()
        );
    }

    @Test
    void no_widen_visibility_keeps_package_private() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-class-pkg-private");
        MavenProject project = new MavenProject(projectRoot);
        Path utilFile = project.sourceRoots().get(0).resolve("com/example/Utils.java");
        String utilSource = Files.readString(utilFile);

        JdtMoveClass.Result result = JdtMoveClass.moveClass(project, utilFile, "com.util", false);

        Approvals.verify(
            RefactoringStoryBoard.titled("Move class without widen_visibility: package-private preserved")
                .javaSection("Input: Utils.java (package-private)", utilSource)
                .refactoring("move class",
                    "`com.example.Utils` → `com.util.Utils` (widen_visibility=false)",
                    "new path: " + result.newFilePath().getFileName())
                .javaSection("Output: Utils.java (new location)", result.newClassSource())
                .build()
        );
    }

    @Test
    void move_class_rejected_when_already_in_target_package() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-class");
        MavenProject project = new MavenProject(projectRoot);
        Path calcFile = project.sourceRoots().get(0)
                .resolve("com/example/service/Calculator.java");
        String calcSource = Files.readString(calcFile);

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtMoveClass.moveClass(project, calcFile, "com.example.service")).actual();

        Approvals.verify(
            RefactoringStoryBoard.titled("Move class rejected: already in target package")
                .javaSection("Input: Calculator.java", calcSource)
                .refactoring("move class",
                        "`com.example.service.Calculator` → `com.example.service` (same package)",
                        "target: com.example.service")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
