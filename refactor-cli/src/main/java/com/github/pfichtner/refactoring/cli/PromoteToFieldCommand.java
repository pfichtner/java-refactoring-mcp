package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtPromoteToField;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/** CLI subcommand for promoting a local variable to a field. */
@Command(name = "promote-to-field", mixinStandardHelpOptions = true,
         description = "Promote a local variable declaration to a private instance field.")
public class PromoteToFieldCommand extends SingleFileCommand {

    @Mixin LocatorOptions locatorOptions;

    @Override protected int resolveOffset(String source, String unitName) {
        return locatorOptions.resolveOffset(source, unitName);
    }
    @Override protected String transform(String source, String unitName, int offset) throws Exception {
        return JdtPromoteToField.promote(source, unitName, offset);
    }
    @Override protected String successMessage(Path f) {
        return "Promoted local variable to field in " + f.getFileName();
    }
}
