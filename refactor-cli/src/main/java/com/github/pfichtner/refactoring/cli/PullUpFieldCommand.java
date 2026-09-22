package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtPullUpField;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;

/** CLI subcommand for pull-up-field refactoring. Logic lives in {@link JdtPullUpField}. */
@Command(name = "pull-up-field", mixinStandardHelpOptions = true,
         description = "Move a field from a subclass up to its direct superclass.")
public class PullUpFieldCommand extends ProjectWideLocatorCommand {
    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtPullUpField.pullUp(p, f, offset);
    }
    @Override protected String successLine(int n) { return "Pulled up field into " + n + " file(s):"; }
}
