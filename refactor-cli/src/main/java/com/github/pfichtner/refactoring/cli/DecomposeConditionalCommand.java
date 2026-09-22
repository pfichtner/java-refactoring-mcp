package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtDecomposeConditional;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/** CLI subcommand for decompose-conditional refactoring. Logic lives in {@link JdtDecomposeConditional}. */
@Command(name = "decompose-conditional", mixinStandardHelpOptions = true,
         description = "Extract a compound boolean condition from an if/while/for statement " +
                       "into a private boolean method with a meaningful name.")
public class DecomposeConditionalCommand extends SingleFileCommand {

    @Option(names = "--name", required = true,
            description = "Name for the extracted boolean method (e.g. 'isAdultPremium').")
    String methodName;

    @Mixin LocatorOptions locator;

    @Override protected int resolveOffset(String source, String unitName) {
        return locator.resolveOffset(source, unitName);
    }
    @Override protected String transform(String source, String unitName, int offset) throws Exception {
        return JdtDecomposeConditional.decomposeConditional(source, unitName, offset, methodName);
    }
    @Override protected String successMessage(Path f) {
        return "Conditional decomposed: " + f.getFileName();
    }
}
