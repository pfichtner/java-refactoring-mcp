package com.github.pfichtner.cli;

import com.github.pfichtner.JdtPromoteToField;
import com.github.pfichtner.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI subcommand for promoting a local variable to a field.
 */
@Command(
    name = "promote-to-field",
    mixinStandardHelpOptions = true,
    description = "Promote a local variable declaration to a private instance field."
)
public class PromoteToFieldCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the local variable.")
    Path file;

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number inside the local variable declaration.")
    int line;

    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number inside the local variable declaration.")
    int column;

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
        String result = JdtPromoteToField.promote(
                source, absFile.getFileName().toString(), offset);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no file written.");
            out.println();
            out.println(result);
        } else {
            Files.writeString(absFile, result);
            out.println("Promoted local variable to field in " + absFile.getFileName());
        }
        return 0;
    }
}
