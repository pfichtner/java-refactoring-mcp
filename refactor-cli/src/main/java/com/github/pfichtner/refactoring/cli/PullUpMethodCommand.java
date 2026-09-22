package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtPullUpMethod;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;

/** CLI subcommand for pull-up-method refactoring. Logic lives in {@link JdtPullUpMethod}. */
@Command(
    name = "pull-up",
    mixinStandardHelpOptions = true,
    description = "Move a method from a subclass up to its direct superclass."
)
public class PullUpMethodCommand extends HierarchyMoveCommand {
    @Override
    protected Map<Path, String> execute(JavaProject proj, Path absFile, int offset) throws Exception {
        return JdtPullUpMethod.pullUp(proj, absFile, offset);
    }
    @Override protected String verb() { return "Pulled up method"; }
}
