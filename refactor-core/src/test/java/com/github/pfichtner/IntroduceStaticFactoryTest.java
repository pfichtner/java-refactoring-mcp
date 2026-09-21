package com.github.pfichtner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.project.MavenProject;
import com.github.pfichtner.support.Fixtures;
import com.github.pfichtner.support.RefactoringStoryBoard;

class IntroduceStaticFactoryTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void introduce_static_factory_creates_method_and_updates_call_sites() throws Exception {
        Path projectRoot   = fixtures.projectPath("projects/static-factory");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot       = project.sourceRoots().get(0);
        Path counterFile   = srcRoot.resolve("com/example/Counter.java");
        Path appFile       = srcRoot.resolve("com/example/App.java");

        String counterSrc = Files.readString(counterFile);
        String appSrc     = Files.readString(appFile);

        int offset = Fixtures.offsetOf(counterSrc, "Counter(int");

        Map<Path, String> changed = JdtIntroduceStaticFactory.introduceStaticFactory(
                project, counterFile, offset, "of", false);

        assertThat(changed.size()).as("Counter.java and App.java should change").isEqualTo(2);

        Path absCounter = counterFile.toAbsolutePath().normalize();
        Path absApp     = appFile.toAbsolutePath().normalize();
        assertThat(changed.containsKey(absCounter)).as("Counter.java must be in result").isTrue();
        assertThat(changed.containsKey(absApp)).as("App.java must be in result").isTrue();

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce static factory: Counter.of (constructor stays public)")
                .inputProject(Map.of("Counter.java", counterSrc, "App.java", appSrc))
                .refactoring("introduce static factory",
                    "`Counter(int,String)` → `Counter.of`",
                    Fixtures.lineCol(counterSrc, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void introduce_static_factory_makes_constructor_private() throws Exception {
        Path projectRoot   = fixtures.projectPath("projects/static-factory");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot       = project.sourceRoots().get(0);
        Path counterFile   = srcRoot.resolve("com/example/Counter.java");
        Path appFile       = srcRoot.resolve("com/example/App.java");

        String counterSrc = Files.readString(counterFile);
        String appSrc     = Files.readString(appFile);

        int offset = Fixtures.offsetOf(counterSrc, "Counter(int");

        Map<Path, String> changed = JdtIntroduceStaticFactory.introduceStaticFactory(
                project, counterFile, offset, "create", true);

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce static factory: Counter.create (constructor private)")
                .inputProject(Map.of("Counter.java", counterSrc, "App.java", appSrc))
                .refactoring("introduce static factory",
                    "`Counter(int,String)` → `Counter.create` + private constructor",
                    Fixtures.lineCol(counterSrc, offset))
                .outputProject(changed)
                .build()
        );
    }

    @Test
    void introduce_static_factory_rejected_when_no_constructor_at_offset() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/static-factory");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot   = project.sourceRoots().get(0);
        Path counterFile = srcRoot.resolve("com/example/Counter.java");
        String source  = Files.readString(counterFile);
        // Point at a regular method, not the constructor
        int offset = Fixtures.offsetOf(source, "value()");

        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtIntroduceStaticFactory.introduceStaticFactory(
                project, counterFile, offset, "of", false)).actual();
        assertThat(ex.getMessage()).contains("No constructor");

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce static factory rejected: no constructor at offset")
                .javaSection("Input: Counter.java", source)
                .refactoring("introduce static factory", "`Counter.of`",
                    Fixtures.lineCol(source, offset))
                .diagnostic(ex.getMessage())
                .build()
        );
    }

    @Test
    void introduce_static_factory_rejected_when_factory_name_already_exists() throws Exception {
        Path projectRoot = fixtures.projectPath("projects/static-factory");
        MavenProject project = new MavenProject(projectRoot);
        Path srcRoot   = project.sourceRoots().get(0);
        Path counterFile = srcRoot.resolve("com/example/Counter.java");
        String source  = Files.readString(counterFile);
        int offset = Fixtures.offsetOf(source, "Counter(int");

        // First call succeeds
        Map<Path, String> first = JdtIntroduceStaticFactory.introduceStaticFactory(
                project, counterFile, offset, "of", false);

        // Second call on the modified source should fail
        String modifiedSource = first.get(counterFile.toAbsolutePath().normalize());
        Path tmp = Files.createTempFile("Counter", ".java");
        Files.writeString(tmp, modifiedSource);

        // Point at the constructor in the modified source
        int offset2 = Fixtures.offsetOf(modifiedSource, "Counter(int");
        IllegalArgumentException ex = assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> JdtIntroduceStaticFactory.introduceStaticFactory(
                project, tmp, offset2, "of", false)).actual();
        assertThat(ex.getMessage()).contains("already has a method");

        Files.deleteIfExists(tmp);

        Approvals.verify(
            RefactoringStoryBoard.titled("Introduce static factory rejected: factory method already exists")
                .javaSection("Input: Counter.java (already has 'of')", modifiedSource)
                .refactoring("introduce static factory", "duplicate `Counter.of`",
                    Fixtures.lineCol(modifiedSource, offset2))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
