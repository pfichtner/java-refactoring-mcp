package com.github.pfichtner.cli;

import com.github.pfichtner.JdtExtractVariable;
import com.github.pfichtner.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI subcommand for extract-variable refactoring.
 * All logic lives in {@link JdtExtractVariable}.
 */
@Command(
    name = "extract-var",
    mixinStandardHelpOptions = true,
    description = "Extract an expression into a new local variable."
)
public class ExtractVarCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the expression.")
    Path file;

    @Option(names = "--start-line", required = true,
            description = "1-based start line of the expression.")
    int startLine;

    @Option(names = "--start-column", required = true,
            description = "1-based start column of the expression.")
    int startColumn;

    @Option(names = "--end-line", required = true,
            description = "1-based end line of the expression.")
    int endLine;

    @Option(names = "--end-column", required = true,
            description = "1-based end column of the expression (exclusive).")
    int endColumn;

    @Option(names = {"--name", "-n"}, required = true,
            description = "Name for the introduced variable.")
    String varName;

    @Option(names = "--replace-all",
            description = "Replace all identical occurrences in the enclosing block.")
    boolean replaceAll;

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

        String source  = Files.readString(absFile);
        int selStart   = JdtRenamer.toOffset(source, startLine, startColumn);
        int selEnd     = JdtRenamer.toOffset(source, endLine, endColumn);
        String result  = JdtExtractVariable.extractVariable(
                source, absFile.getFileName().toString(),
                selStart, selEnd - selStart, varName, replaceAll);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no file written.");
            out.println();
            out.println(result);
        } else {
            Files.writeString(absFile, result);
            out.println("Extracted variable '" + varName + "' in " + absFile.getFileName());
        }
        return 0;
    }
}
