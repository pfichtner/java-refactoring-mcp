package com.github.pfichtner.refactoring.cli;

import com.github.pfichtner.refactoring.JdtExtractSuperclass;

import picocli.CommandLine.Command;

/** CLI subcommand for extract-superclass. Logic lives in {@link JdtExtractSuperclass}. */
@Command(name = "extract-superclass", mixinStandardHelpOptions = true,
         description = "Move methods into a new abstract superclass and make the class extend it.")
public class ExtractSuperclassCommand extends ExtractHierarchyCommand {
    @Override protected String[] extract(String source, String unitName) throws Exception {
        JdtExtractSuperclass.Result r = JdtExtractSuperclass.extractSuperclass(source, unitName, name, methods);
        return new String[]{r.modifiedClassSource(), r.superclassSource()};
    }
    @Override protected String label() { return "superclass"; }
}
