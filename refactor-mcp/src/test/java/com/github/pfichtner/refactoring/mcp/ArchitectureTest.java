package com.github.pfichtner.refactoring.mcp;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class ArchitectureTest {

    private static final JavaClasses mcpClasses =
        new ClassFileImporter().importPackages("com.github.pfichtner.refactoring.mcp");

    @Test
    void no_regex_usage() {
        noClasses().should()
            .accessClassesThat().resideInAPackage("java.util.regex")
            .check(mcpClasses);
    }

    @Test
    void no_direct_jdt_ast_manipulation() {
        noClasses().should()
            .accessClassesThat().resideInAPackage("org.eclipse.jdt.core.dom..")
            .check(mcpClasses);
    }

    @Test
    void tool_classes_are_final() {
        classes().that().haveSimpleNameEndingWith("Tool")
            .should().haveModifier(JavaModifier.FINAL)
            .check(mcpClasses);
    }
}
