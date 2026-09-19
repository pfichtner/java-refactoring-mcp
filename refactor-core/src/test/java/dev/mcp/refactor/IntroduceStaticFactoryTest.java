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

        assertEquals(2, changed.size(), "Counter.java and App.java should change");

        Path absCounter = counterFile.toAbsolutePath().normalize();
        Path absApp     = appFile.toAbsolutePath().normalize();
        assertTrue(changed.containsKey(absCounter), "Counter.java must be in result");
        assertTrue(changed.containsKey(absApp),     "App.java must be in result");

        String newCounter = changed.get(absCounter);
        String newApp     = changed.get(absApp);

        assertTrue(newCounter.contains("public static Counter of("), "factory method must be present");
        assertTrue(newCounter.contains("return new Counter("), "factory body must use new");
        assertTrue(newApp.contains("Counter.of("), "App call sites must use factory");
        assertFalse(newApp.contains("new Counter("), "App must not contain direct new");

        Approvals.verify(
            RenameStoryBoard.titled("Introduce static factory: Counter.of (constructor stays public)")
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

        String newCounter = changed.get(counterFile.toAbsolutePath().normalize());
        assertTrue(newCounter.contains("private Counter("), "constructor must become private");
        assertTrue(newCounter.contains("public static Counter create("), "factory must be public static");

        Approvals.verify(
            RenameStoryBoard.titled("Introduce static factory: Counter.create (constructor private)")
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

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdtIntroduceStaticFactory.introduceStaticFactory(
                    project, counterFile, offset, "of", false));
        assertTrue(ex.getMessage().contains("No constructor"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Introduce static factory rejected: no constructor at offset")
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
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> JdtIntroduceStaticFactory.introduceStaticFactory(
                    project, tmp, offset2, "of", false));
        assertTrue(ex.getMessage().contains("already has a method"), ex.getMessage());

        Files.deleteIfExists(tmp);

        Approvals.verify(
            RenameStoryBoard.titled("Introduce static factory rejected: factory method already exists")
                .javaSection("Input: Counter.java (already has 'of')", modifiedSource)
                .refactoring("introduce static factory", "duplicate `Counter.of`",
                    Fixtures.lineCol(modifiedSource, offset2))
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
