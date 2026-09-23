package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtExtractConstant;
import com.github.pfichtner.refactoring.SourceUnit;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for extract-constant refactoring. Logic lives in {@link JdtExtractConstant}. */
@Command(name = "extract-const", mixinStandardHelpOptions = true,
         description = "Extract an expression into a private static final constant.")
public class ExtractConstCommand extends SelectionCommand {

    @Option(names = {"--name", "-n"}, required = true) String constName;
    @Option(names = "--replace-all") boolean replaceAll;

    @Override protected String transform(SourceUnit unit, int selStart, int selLen) throws Exception {
        return JdtExtractConstant.extractConstant(unit, selStart, selLen, constName, replaceAll);
    }
    @Override protected String successMessage(Path f) {
        return "Extracted constant '" + constName + "' in " + f.getFileName();
    }
}
