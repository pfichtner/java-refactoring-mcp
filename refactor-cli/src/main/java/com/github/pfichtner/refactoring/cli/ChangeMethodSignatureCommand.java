package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtChangeMethodSignature;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for change-method-signature refactoring. Logic lives in {@link JdtChangeMethodSignature}. */
@Command(name = "change-method-signature", mixinStandardHelpOptions = true,
         description = "Change a method's return type and/or reorder its parameters. " +
                       "Call sites are updated when parameters are reordered.")
public class ChangeMethodSignatureCommand extends ProjectWideLocatorCommand {

    @Option(names = "--return-type",
            description = "New return type (e.g. 'double', 'List<String>'). Omit to leave unchanged.")
    String newReturnType;
    @Option(names = "--param-order", split = ",",
            description = "Comma-separated 0-based parameter indices giving the new order " +
                          "(e.g. '1,0' to swap two params). Omit to leave unchanged.")
    int[] paramOrder;

    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtChangeMethodSignature.changeSignature(p, f, offset, newReturnType, paramOrder);
    }
    @Override protected String successLine(int n) { return "Signature changed; " + n + " file(s) updated:"; }
}
