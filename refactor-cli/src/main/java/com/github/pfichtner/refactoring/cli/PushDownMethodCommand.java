package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtPushDownMethod;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;

/** CLI subcommand for push-down-method refactoring. Logic lives in {@link JdtPushDownMethod}. */
@Command(name = "push-down", mixinStandardHelpOptions = true,
         description = "Move a method from a class down into all direct subclasses in the project.")
public class PushDownMethodCommand extends ProjectWideLocatorCommand {
    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtPushDownMethod.pushDown(p, f, offset);
    }
    @Override protected String successLine(int n) { return "Pushed down method into " + n + " file(s):"; }
}
