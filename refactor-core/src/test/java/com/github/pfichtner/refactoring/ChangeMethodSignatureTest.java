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

/**
 * Approval tests for Change Method Signature.
 * Covers parameter reordering (multi-file call sites) and return type change (declaration only).
 */
class ChangeMethodSignatureTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    // -------------------------------------------------------------------------
    // Parameter reorder
    // -------------------------------------------------------------------------

    @Test
    void reorder_params_updates_declaration_and_call_sites() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/change-method-signature");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot = project.sourceRoots().get(0);
        Path converterFile = srcRoot.resolve("com/example/Converter.java");
        Path appFile       = srcRoot.resolve("com/example/App.java");

        String converterSrc = Files.readString(converterFile);
        String appSrc       = Files.readString(appFile);

        // convert(int value, String prefix) → convert(String prefix, int value)
        int offset = Fixtures.offsetOf(converterSrc, "convert(int");
        Map<Path, String> changed = JdtChangeMethodSignature.changeSignature(
                project, converterFile, offset, null, new int[]{1, 0});

        Path absConverter = converterFile.toAbsolutePath().normalize();
        Path absApp       = appFile.toAbsolutePath().normalize();

        assertThat(changed).containsKey(absConverter);
        assertThat(changed).containsKey(absApp);

        Approvals.verify(
            RefactoringStoryBoard.titled("Change method signature: reorder params convert(int,String) → convert(String,int)")
                .inputProject(Map.of("App.java", appSrc, "Converter.java", converterSrc))
                .refactoring("change method signature",
                        "`convert(int value, String prefix)` → `convert(String prefix, int value)`",
                        "paramOrder: [1, 0] — all call sites updated")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Return type change
    // -------------------------------------------------------------------------

    @Test
    void change_return_type_updates_declaration_only() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/change-method-signature");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot = project.sourceRoots().get(0);
        Path converterFile = srcRoot.resolve("com/example/Converter.java");

        String converterSrc = Files.readString(converterFile);

        int offset = Fixtures.offsetOf(converterSrc, "convert(int");
        Map<Path, String> changed = JdtChangeMethodSignature.changeSignature(
                project, converterFile, offset, "Object", null);

        Path absConverter = converterFile.toAbsolutePath().normalize();
        assertThat(changed).containsKey(absConverter);
        assertThat(changed).doesNotContainKey(converterFile.getParent().resolve("App.java").toAbsolutePath().normalize());

        Approvals.verify(
            RefactoringStoryBoard.titled("Change method signature: change return type String → Object")
                .javaSection("Input: Converter.java", converterSrc)
                .refactoring("change method signature",
                        "`String convert(...)` → `Object convert(...)`",
                        "declaration only — call sites unchanged")
                .javaSection("Output: Converter.java", changed.get(absConverter))
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Combined: reorder + return type
    // -------------------------------------------------------------------------

    @Test
    void reorder_and_return_type_change_applied_together() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/change-method-signature");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot = project.sourceRoots().get(0);
        Path converterFile = srcRoot.resolve("com/example/Converter.java");
        Path appFile       = srcRoot.resolve("com/example/App.java");

        String converterSrc = Files.readString(converterFile);
        String appSrc       = Files.readString(appFile);

        int offset = Fixtures.offsetOf(converterSrc, "convert(int");
        Map<Path, String> changed = JdtChangeMethodSignature.changeSignature(
                project, converterFile, offset, "Object", new int[]{1, 0});

        assertThat(changed).containsKey(converterFile.toAbsolutePath().normalize());
        assertThat(changed).containsKey(appFile.toAbsolutePath().normalize());

        Approvals.verify(
            RefactoringStoryBoard.titled("Change method signature: reorder params + return type Object")
                .inputProject(Map.of("App.java", appSrc, "Converter.java", converterSrc))
                .refactoring("change method signature",
                        "`String convert(int value, String prefix)` → `Object convert(String prefix, int value)`",
                        "paramOrder: [1, 0] + return type Object")
                .outputProject(changed)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Rejection cases
    // -------------------------------------------------------------------------

    @Test
    void rejected_when_nothing_to_change() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/change-method-signature");
        MavenProject project = new MavenProject(projectRoot);
        Path converterFile = project.sourceRoots().get(0).resolve("com/example/Converter.java");
        String source = Files.readString(converterFile);
        int offset = Fixtures.offsetOf(source, "convert(int");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtChangeMethodSignature.changeSignature(
                        project, converterFile, offset, null, null))
                .actual();

        assertThat(ex.getMessage()).contains("Nothing to change");

        Approvals.verify(
            RefactoringStoryBoard.titled("Change method signature rejected: nothing to change")
                .javaSection("Input: Converter.java", source)
                .refactoring("change method signature",
                        "`convert` — no newReturnType, no paramOrder",
                        "")
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void rejected_when_param_order_length_wrong() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/change-method-signature");
        MavenProject project = new MavenProject(projectRoot);
        Path converterFile = project.sourceRoots().get(0).resolve("com/example/Converter.java");
        String source = Files.readString(converterFile);
        int offset = Fixtures.offsetOf(source, "convert(int");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtChangeMethodSignature.changeSignature(
                        project, converterFile, offset, null, new int[]{0}))
                .actual();

        assertThat(ex.getMessage()).contains("does not match parameter count");

        Approvals.verify(
            RefactoringStoryBoard.titled("Change method signature rejected: wrong paramOrder length")
                .javaSection("Input: Converter.java", source)
                .refactoring("change method signature",
                        "`convert` — paramOrder length 1 but method has 2 params",
                        "")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
