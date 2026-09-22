package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtMoveMethod;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for move-method refactoring. Logic lives in {@link JdtMoveMethod}. */
@Command(name = "move-method", mixinStandardHelpOptions = true,
         description = "Move a method from one class to another class within the project.")
public class MoveMethodCommand extends ProjectWideLocatorCommand {

    @Option(names = {"--target-class", "-t"}, required = true,
            description = "Fully-qualified name of the target class (e.g. 'com.example.Report').")
    String targetClass;

    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtMoveMethod.moveMethod(p, f, offset, targetClass);
    }
    @Override protected String successLine(int n) { return "Moved method into " + n + " file(s):"; }
}
