package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtExtractor;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CLI subcommand for extract-method refactoring.
 * All extraction logic lives in {@link JdtExtractor}.
 */
@Command(name = "extract", mixinStandardHelpOptions = true,
         description = "Extract selected statements into a new private method.")
public class ExtractCommand extends SelectionCommand {

    @Option(names = {"--name", "-n"}, required = true,
            description = "Name for the extracted method.")
    String methodName;

    @Override protected String transform(String source, String unitName, int selStart, int selLen) throws Exception {
        return JdtExtractor.extractMethod(source, unitName, selStart, selLen, methodName);
    }
    @Override protected String successMessage(Path f) {
        return "Extracted '" + methodName + "' in " + f.getFileName();
    }
}
