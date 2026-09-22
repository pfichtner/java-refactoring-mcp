package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtRemoveParam;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;

/** CLI subcommand for remove-parameter refactoring. Logic lives in {@link JdtRemoveParam}. */
@Command(name = "remove-param", mixinStandardHelpOptions = true,
         description = "Remove an unused parameter from a method and update all call sites.")
public class RemoveParamCommand extends ProjectWideLocatorCommand {
    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtRemoveParam.removeParam(p, f, offset);
    }
    @Override protected String successLine(int n) { return "Removed parameter in " + n + " file(s):"; }
}
