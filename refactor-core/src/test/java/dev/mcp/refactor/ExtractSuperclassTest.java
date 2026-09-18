package dev.mcp.refactor;

import dev.mcp.refactor.support.Fixtures;
import dev.mcp.refactor.support.RenameStoryBoard;
import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Approval tests for Extract Superclass.
 */
class ExtractSuperclassTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void extract_superclass_moves_methods_and_adds_extends() throws Exception {
        String source = fixtures.load("extract-superclass/simple/input/Animal.java");

        // Move breathe() and eat() to a new Living superclass; keep name() in Animal
        JdtExtractSuperclass.Result result = JdtExtractSuperclass.extractSuperclass(
                source, "Animal.java", "Living", List.of("breathe", "eat"));

        Approvals.verify(
            RenameStoryBoard.titled("Extract superclass: Living from Animal")
                .javaSection("Input: Animal.java", source)
                .refactoring("extract superclass", "`Animal` → extends `Living`",
                        "moves breathe() and eat() to abstract superclass; name() stays in Animal")
                .javaSection("Output: Animal.java", result.modifiedClassSource())
                .javaSection("New: Living.java", result.superclassSource())
                .build()
        );
    }

    @Test
    void extract_all_public_methods_when_no_subset_specified() throws Exception {
        String source = fixtures.load("extract-superclass/simple/input/Animal.java");

        JdtExtractSuperclass.Result result = JdtExtractSuperclass.extractSuperclass(
                source, "Animal.java", "BaseAnimal", List.of());

        Approvals.verify(
            RenameStoryBoard.titled("Extract superclass: BaseAnimal (all public methods)")
                .javaSection("Input: Animal.java", source)
                .refactoring("extract superclass", "`Animal` → extends `BaseAnimal`",
                        "all public methods moved: breathe, eat, name")
                .javaSection("Output: Animal.java", result.modifiedClassSource())
                .javaSection("New: BaseAnimal.java", result.superclassSource())
                .build()
        );
    }

    @Test
    void extract_rejected_when_class_already_extends() throws Exception {
        String source = fixtures.load("extract-superclass/invalid-already-extends/input/Dog.java");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JdtExtractSuperclass.extractSuperclass(
                        source, "Dog.java", "Canine", List.of()));
        assertTrue(ex.getMessage().contains("already extends"), ex.getMessage());

        Approvals.verify(
            RenameStoryBoard.titled("Extract superclass — rejected: class already extends")
                .javaSection("Input: Dog.java", source)
                .refactoring("extract superclass", "`Dog` → extends `Canine`",
                        "Dog already extends Animal — chaining not supported")
                .diagnostic(ex.getMessage())
                .build()
        );
    }
}
