package com.github.pfichtner.refactoring.cli;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class ArchitectureTest {

    private static final JavaClasses cliClasses =
        new ClassFileImporter().importPackages("com.github.pfichtner.refactoring.cli");

    @Test
    void no_regex_usage() {
        noClasses().should()
            .accessClassesThat().resideInAPackage("java.util.regex")
            .check(cliClasses);
    }

    @Test
    void no_direct_jdt_ast_manipulation() {
        noClasses().should()
            .accessClassesThat().resideInAPackage("org.eclipse.jdt.core.dom..")
            .check(cliClasses);
    }

    @Test
    void concrete_command_classes_extend_abstract_base() {
        classes().that().haveSimpleNameEndingWith("Command")
            .and().doNotHaveModifier(JavaModifier.ABSTRACT)
            .should().beAssignableTo(AbstractRefactoringCommand.class)
            .check(cliClasses);
    }
}
