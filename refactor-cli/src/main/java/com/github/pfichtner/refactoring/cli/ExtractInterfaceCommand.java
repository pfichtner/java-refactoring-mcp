package com.github.pfichtner.refactoring.cli;

import com.github.pfichtner.refactoring.JdtExtractInterface;

import picocli.CommandLine.Command;

/** CLI subcommand for extract-interface refactoring. Logic lives in {@link JdtExtractInterface}. */
@Command(name = "extract-interface", mixinStandardHelpOptions = true,
         description = "Extract a new interface from the public methods of a class.")
public class ExtractInterfaceCommand extends ExtractHierarchyCommand {
    @Override protected String[] extract(String source, String unitName) throws Exception {
        JdtExtractInterface.Result r = JdtExtractInterface.extractInterface(source, unitName, name, methods);
        return new String[]{r.modifiedClassSource(), r.interfaceSource()};
    }
    @Override protected String label() { return "interface"; }
}
