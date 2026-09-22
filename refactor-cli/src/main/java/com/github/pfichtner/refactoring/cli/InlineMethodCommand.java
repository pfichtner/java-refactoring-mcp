package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtInlineMethod;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for inline-method refactoring. All logic lives in {@link JdtInlineMethod}. */
@Command(name = "inline-method", mixinStandardHelpOptions = true,
         description = "Inline a method call: replace it with the method body.")
public class InlineMethodCommand extends ProjectWideLocatorCommand {

    @Option(names = "--remove-declaration",
            description = "Also delete the method declaration after inlining.")
    boolean removeDeclaration;

    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtInlineMethod.inlineMethod(p, f, offset, removeDeclaration);
    }
    @Override protected String successLine(int n) { return "Inlined method in " + n + " file(s):"; }
}
