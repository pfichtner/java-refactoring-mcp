package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtRemoveMethod;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for remove-method refactoring. Logic lives in {@link JdtRemoveMethod}. */
@Command(name = "remove-method", mixinStandardHelpOptions = true,
         description = "Remove a method from a class or interface, optionally cascading to overriding/implementing methods.")
public class RemoveMethodCommand extends ProjectWideLocatorCommand {

    @Option(names = "--no-cascade",
            description = "Skip removal in overriding and implementing methods (cascade is on by default).")
    boolean noCascade;

    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtRemoveMethod.removeMethod(p, f, offset, !noCascade);
    }
    @Override protected String successLine(int n) { return "Removed method in " + n + " file(s):"; }
}
