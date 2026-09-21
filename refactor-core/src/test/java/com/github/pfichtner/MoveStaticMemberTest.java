package com.github.pfichtner;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RefactoringStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MoveStaticMemberTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void move_static_method_to_another_class_and_update_call_sites() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-static");
        MavenProject project = new MavenProject(projectRoot);
        Path sourceRoot = project.sourceRoots().get(0);

        Path mathFile   = sourceRoot.resolve("com/example/MathUtils.java");
        Path helpersFile = sourceRoot.resolve("com/example/Helpers.java");
        Path clientFile = sourceRoot.resolve("com/example/Client.java");

        String mathSource    = Files.readString(mathFile);
        String helpersSource = Files.readString(helpersFile);
        String clientSource  = Files.readString(clientFile);

        int offset = Fixtures.offsetOf(mathSource, "square");

        Map<Path, String> result = JdtMoveStaticMember.moveStaticMember(
                project, mathFile, offset, "com.example.Helpers");

        assertThat(result).containsKey(mathFile.toAbsolutePath().normalize());
        assertThat(result).containsKey(helpersFile.toAbsolutePath().normalize());
        assertThat(result).containsKey(clientFile.toAbsolutePath().normalize());

        String newMath    = result.get(mathFile.toAbsolutePath().normalize());
        String newHelpers = result.get(helpersFile.toAbsolutePath().normalize());
        String newClient  = result.get(clientFile.toAbsolutePath().normalize());

        Approvals.verify(
                RefactoringStoryBoard.titled("Move static method MathUtils.square → Helpers.square")
                        .javaSection("Input: MathUtils.java", mathSource)
                        .javaSection("Input: Helpers.java", helpersSource)
                        .javaSection("Input: Client.java", clientSource)
                        .refactoring("move static member",
                                "`MathUtils.square(int)` → `Helpers.square(int)`; call sites updated",
                                Fixtures.lineCol(mathSource, offset))
                        .javaSection("Output: MathUtils.java", newMath)
                        .javaSection("Output: Helpers.java", newHelpers)
                        .javaSection("Output: Client.java", newClient)
                        .build()
        );
    }

    @Test
    void move_static_member_rejected_when_no_static_member_at_offset() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/move-static");
        MavenProject project = new MavenProject(projectRoot);
        Path sourceRoot = project.sourceRoots().get(0);
        Path mathFile = sourceRoot.resolve("com/example/MathUtils.java");
        String mathSource = Files.readString(mathFile);
        int offset = Fixtures.offsetOf(mathSource, "class MathUtils");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> JdtMoveStaticMember.moveStaticMember(project, mathFile, offset, "com.example.Helpers"))
                .actual();
        assertThat(ex.getMessage()).contains("No static");

        Approvals.verify(
                RefactoringStoryBoard.titled("Move static member rejected: no static member at offset")
                        .javaSection("Input: MathUtils.java", mathSource)
                        .refactoring("move static member",
                                "offset inside class declaration, not a static method or field",
                                Fixtures.lineCol(mathSource, offset))
                        .diagnostic(ex.getMessage())
                        .build()
        );
    }
}
