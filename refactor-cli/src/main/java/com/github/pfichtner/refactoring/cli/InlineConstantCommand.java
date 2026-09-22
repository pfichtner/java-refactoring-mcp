package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtInliner;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/** CLI subcommand for inline-constant refactoring. All logic lives in {@link JdtInliner}. */
@Command(name = "inline-const", mixinStandardHelpOptions = true,
         description = "Inline a static final constant: replace references with its initializer.")
public class InlineConstantCommand extends SingleFileCommand {

    @Mixin LocatorOptions locator;

    @Option(names = "--all-occurrences",
            description = "Replace all references in the file (default: only this reference).")
    boolean allOccurrences;
    @Option(names = "--remove-declaration",
            description = "Also delete the constant declaration (requires --all-occurrences).")
    boolean removeDeclaration;

    @Override protected int resolveOffset(String source, String unitName) {
        return locator.resolveOffset(source, unitName);
    }
    @Override protected String transform(String source, String unitName, int offset) throws Exception {
        return JdtInliner.inlineConstant(source, unitName, offset, allOccurrences, removeDeclaration);
    }
    @Override protected String successMessage(Path f) {
        return "Inlined constant in " + f.getFileName();
    }
}
