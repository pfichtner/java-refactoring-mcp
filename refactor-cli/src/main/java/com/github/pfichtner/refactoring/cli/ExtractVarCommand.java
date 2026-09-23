package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtExtractVariable;
import com.github.pfichtner.refactoring.SourceUnit;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CLI subcommand for extract-variable refactoring.
 * All logic lives in {@link JdtExtractVariable}.
 */
@Command(name = "extract-var", mixinStandardHelpOptions = true,
         description = "Extract an expression into a new local variable.")
public class ExtractVarCommand extends SelectionCommand {

    @Option(names = {"--name", "-n"}, required = true,
            description = "Name for the introduced variable.")
    String varName;
    @Option(names = "--replace-all",
            description = "Replace all identical occurrences in the enclosing block.")
    boolean replaceAll;

    @Override protected String transform(SourceUnit unit, int selStart, int selLen) throws Exception {
        return JdtExtractVariable.extractVariable(unit, selStart, selLen, varName, replaceAll);
    }
    @Override protected String successMessage(Path f) {
        return "Extracted variable '" + varName + "' in " + f.getFileName();
    }
}
