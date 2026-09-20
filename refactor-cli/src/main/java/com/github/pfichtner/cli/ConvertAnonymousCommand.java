package com.github.pfichtner.cli;

import com.github.pfichtner.JdtConvertAnonymousToNested;
import com.github.pfichtner.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI subcommand for converting an anonymous class to a named nested class.
 */
@Command(
    name = "convert-anonymous",
    mixinStandardHelpOptions = true,
    description = "Convert an anonymous class to a named nested class."
)
public class ConvertAnonymousCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the anonymous class.")
    Path file;

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number inside the anonymous class.")
    int line;

    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number inside the anonymous class.")
    int column;

    @Option(names = {"--name", "-n"}, required = true,
            description = "Simple name for the new nested class.")
    String nestedClassName;

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
        String result = JdtConvertAnonymousToNested.convert(
                source, absFile.getFileName().toString(), offset, nestedClassName);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no file written.");
            out.println();
            out.println(result);
        } else {
            Files.writeString(absFile, result);
            out.println("Converted anonymous class to nested class '" + nestedClassName + "' in " + absFile.getFileName());
        }
        return 0;
    }
}
