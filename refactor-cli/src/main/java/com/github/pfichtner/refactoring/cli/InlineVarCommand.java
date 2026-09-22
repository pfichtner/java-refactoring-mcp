package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtInliner;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/** CLI subcommand for inline-variable refactoring. All logic lives in {@link JdtInliner}. */
@Command(name = "inline-var", mixinStandardHelpOptions = true,
         description = "Inline a local variable: replace all uses with its initializer and remove the declaration.")
public class InlineVarCommand extends SingleFileCommand {

    @Mixin LocatorOptions locator;

    @Override protected int resolveOffset(String source, String unitName) {
        return locator.resolveOffset(source, unitName);
    }
    @Override protected String transform(String source, String unitName, int offset) throws Exception {
        return JdtInliner.inlineVariable(source, unitName, offset);
    }
    @Override protected String successMessage(Path f) {
        return "Inlined variable in " + f.getFileName();
    }
}
