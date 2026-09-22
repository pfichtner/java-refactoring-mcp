package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.github.pfichtner.refactoring.JdtIntroduceIndirection;
import com.github.pfichtner.refactoring.JdtRenamer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * CLI subcommand for introducing a static indirection wrapper method.
 */
@Command(
    name = "introduce-indirection",
    mixinStandardHelpOptions = true,
    description = "Add a static wrapper method that delegates to the method at the given position."
)
public class IntroduceIndirectionCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the method to wrap.")
    Path file;

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number inside the method declaration.")
    int line;

    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number inside the method declaration.")
    int column;

    @Option(names = {"--name", "-n"}, required = true,
            description = "Name for the new indirection (wrapper) method.")
    String indirectionName;

    @Option(names = "--dry-run",
            description = "Print the result without writing to disk.")
    boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }

        String source = Files.readString(absFile);
        int offset    = JdtRenamer.toOffset(source, line, column);
        String result = JdtIntroduceIndirection.introduceIndirection(
                source, absFile.getFileName().toString(), offset, indirectionName);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no file written.");
            out.println();
            out.println(result);
        } else {
            Files.writeString(absFile, result);
            out.println("Introduced indirection method '" + indirectionName + "' in " + absFile.getFileName());
        }
        return 0;
    }
}
