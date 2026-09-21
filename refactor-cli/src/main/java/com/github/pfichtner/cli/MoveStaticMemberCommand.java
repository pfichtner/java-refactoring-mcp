package com.github.pfichtner.cli;

import com.github.pfichtner.JdtMoveStaticMember;
import com.github.pfichtner.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * CLI subcommand for moving a static member to another class.
 */
@Command(
    name = "move-static",
    mixinStandardHelpOptions = true,
    description = "Move a static method or field to another class, updating all call sites."
)
public class MoveStaticMemberCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the static member.")
    Path file;

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number inside the static member declaration.")
    int line;

    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number inside the static member declaration.")
    int column;

    @Option(names = {"--target", "-t"}, required = true,
            description = "Fully-qualified name of the target class to move the member into (e.g. 'com.example.Helpers').")
    String targetClassName;

    @Mixin ProjectOptions project;

    @Option(names = "--dry-run",
            description = "Print changed sources without writing to disk.")
    boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }

        var javaProject = project.resolve(absFile);
        String source   = Files.readString(absFile);
        int offset      = JdtRenamer.toOffset(source, line, column);

        Map<Path, String> result = JdtMoveStaticMember.moveStaticMember(
                javaProject, absFile, offset, targetClassName);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written. Changed files (" + result.size() + "):");
            for (Map.Entry<Path, String> entry : result.entrySet()) {
                out.println("\n=== " + entry.getKey().getFileName() + " ===");
                out.println(entry.getValue());
            }
        } else {
            for (Map.Entry<Path, String> entry : result.entrySet()) {
                Files.writeString(entry.getKey(), entry.getValue());
            }
            out.println("Moved static member to '" + targetClassName + "'. Changed " + result.size() + " file(s).");
        }
        return 0;
    }
}
