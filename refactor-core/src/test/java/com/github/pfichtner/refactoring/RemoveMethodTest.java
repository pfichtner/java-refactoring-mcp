package com.github.pfichtner.refactoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.project.MavenProject;
import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

/**
 * Approval tests for Remove Method.
 */
class RemoveMethodTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void remove_interface_method_cascades_to_all_implementors() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/remove-method");
        MavenProject project = new MavenProject(projectRoot);

        Path printableFile = project.sourceRoots().get(0)
                .resolve("com/example/Printable.java");
        String source = Files.readString(printableFile);

        // Remove 'print()' from the interface — should cascade to Document and Report
        int offset = Fixtures.offsetOf(source, "void print");

        Map<Path, String> changed = JdtRemoveMethod.removeMethod(project, printableFile, offset, true);

        Map<String, String> inputs = fixtures.loadProjectSources("projects/remove-method/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Remove method: print() from interface — cascades to all implementors")
                .inputProject(inputs)
                .refactoring("remove method", "`void print()` in Printable",
                        Fixtures.lineCol(source, offset) + " in Printable.java — cascade=true")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void remove_interface_method_with_single_implementor() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/remove-method");
        MavenProject project = new MavenProject(projectRoot);

        Path printableFile = project.sourceRoots().get(0)
                .resolve("com/example/Printable.java");
        String source = Files.readString(printableFile);

        // Remove 'describe()' — only Document overrides it (Report does not)
        int offset = Fixtures.offsetOf(source, "String describe");

        Map<Path, String> changed = JdtRemoveMethod.removeMethod(project, printableFile, offset, true);

        Map<String, String> inputs = fixtures.loadProjectSources("projects/remove-method/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Remove method: describe() from interface — only one implementor")
                .inputProject(inputs)
                .refactoring("remove method", "`String describe()` in Printable",
                        Fixtures.lineCol(source, offset) + " in Printable.java — cascade=true")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void remove_class_method_cascades_to_subclasses() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/remove-method");
        MavenProject project = new MavenProject(projectRoot);

        Path documentFile = project.sourceRoots().get(0)
                .resolve("com/example/Document.java");
        String source = Files.readString(documentFile);

        // Remove 'print()' from Document — should cascade to Report
        int offset = Fixtures.offsetOf(source, "void print");

        Map<Path, String> changed = JdtRemoveMethod.removeMethod(project, documentFile, offset, true);

        Map<String, String> inputs = fixtures.loadProjectSources("projects/remove-method/src/main/java");

        Approvals.verify(
            RefactoringStoryBoard.titled("Remove method: print() from class — cascades to subclasses")
                .inputProject(inputs)
                .refactoring("remove method", "`void print()` in Document",
                        Fixtures.lineCol(source, offset) + " in Document.java — cascade=true")
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void remove_method_no_cascade() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/remove-method");
        MavenProject project = new MavenProject(projectRoot);

        Path documentFile = project.sourceRoots().get(0)
                .resolve("com/example/Document.java");
        String source = Files.readString(documentFile);

        // Remove 'print()' from Document only — Report's override stays
        int offset = Fixtures.offsetOf(source, "void print");

        Map<Path, String> changed = JdtRemoveMethod.removeMethod(project, documentFile, offset, false);

        assertThat(changed).containsOnlyKeys(documentFile.toAbsolutePath().normalize());

        Approvals.verify(
            RefactoringStoryBoard.titled("Remove method: print() from Document only — cascade=false")
                .inputProject(fixtures.loadProjectSources("projects/remove-method/src/main/java"))
                .refactoring("remove method", "`void print()` in Document",
                        Fixtures.lineCol(source, offset) + " in Document.java — cascade=false, Report's override kept")
                .outputProject(changed)
                .build()
        );
    }
}
