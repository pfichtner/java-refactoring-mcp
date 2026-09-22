package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtPullUpMethod;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;

/** CLI subcommand for pull-up-method refactoring. Logic lives in {@link JdtPullUpMethod}. */
@Command(name = "pull-up", mixinStandardHelpOptions = true,
         description = "Move a method from a subclass up to its direct superclass.")
public class PullUpMethodCommand extends ProjectWideLocatorCommand {
    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtPullUpMethod.pullUp(p, f, offset);
    }
    @Override protected String successLine(int n) { return "Pulled up method into " + n + " file(s):"; }
}
