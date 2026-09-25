package com.github.pfichtner.refactoring;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class ArchitectureTest {

    private static final JavaClasses coreClasses =
        new ClassFileImporter().importPackages("com.github.pfichtner.refactoring");

    @Test
    void jdt_engine_classes_do_not_use_regex() {
        // GradleProject may use regex to parse build files; Jdt* engine classes must not
        noClasses().that().haveSimpleNameStartingWith("Jdt")
            .should().accessClassesThat().resideInAPackage("java.util.regex")
            .check(coreClasses);
    }

    @Test
    void locator_does_not_depend_on_project() {
        noClasses().that().resideInAPackage("..locator..")
            .should().dependOnClassesThat().resideInAPackage("..project..")
            .check(coreClasses);
    }

    @Test
    void project_does_not_depend_on_locator() {
        noClasses().that().resideInAPackage("..project..")
            .should().dependOnClassesThat().resideInAPackage("..locator..")
            .check(coreClasses);
    }

    @Test
    void no_package_cycles() {
        slices().matching("com.github.pfichtner.refactoring.(*)..").should().beFreeOfCycles()
            .check(coreClasses);
    }

    @Test
    void jdt_engine_classes_reside_in_root_package() {
        classes().that().haveSimpleNameStartingWith("Jdt")
            .should().resideInAPackage("com.github.pfichtner.refactoring")
            .check(coreClasses);
    }
}
