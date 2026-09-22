package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtMoveStaticMember;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for moving a static member to another class. */
@Command(name = "move-static", mixinStandardHelpOptions = true,
         description = "Move a static method or field to another class, updating all call sites.")
public class MoveStaticMemberCommand extends ProjectWideLocatorCommand {

    @Option(names = {"--target", "-t"}, required = true,
            description = "Fully-qualified name of the target class (e.g. 'com.example.Helpers').")
    String targetClassName;

    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtMoveStaticMember.moveStaticMember(p, f, offset, targetClassName);
    }
    @Override protected String successLine(int n) {
        return "Moved static member to '" + targetClassName + "' in " + n + " file(s):";
    }
}
