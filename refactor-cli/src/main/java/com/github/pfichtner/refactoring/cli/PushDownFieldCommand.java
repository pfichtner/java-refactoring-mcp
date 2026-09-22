package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtPushDownField;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;

/** CLI subcommand for push-down-field refactoring. Logic lives in {@link JdtPushDownField}. */
@Command(name = "push-down-field", mixinStandardHelpOptions = true,
         description = "Move a field from a class down to every direct subclass.")
public class PushDownFieldCommand extends ProjectWideLocatorCommand {
    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtPushDownField.pushDown(p, f, offset);
    }
    @Override protected String successLine(int n) { return "Pushed down field into " + n + " file(s):"; }
}
