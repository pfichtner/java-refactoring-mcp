package com.github.pfichtner.cli;

import com.github.pfichtner.JdtExtractor;
import com.github.pfichtner.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI subcommand for extract-method refactoring.
 * All extraction logic lives in {@link JdtExtractor}.
 */
@Command(
    name = "extract",
    mixinStandardHelpOptions = true,
    description = "Extract selected statements into a new private method."
)
public class ExtractCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file to refactor.")
    Path file;

    @Option(names = "--start-line", required = true,
            description = "1-based start line of the selection.")
    int startLine;

    @Option(names = "--start-column", required = true,
            description = "1-based start column of the selection.")
    int startColumn;

    @Option(names = "--end-line", required = true,
            description = "1-based end line of the selection.")
    int endLine;

    @Option(names = "--end-column", required = true,
            description = "1-based end column of the selection (exclusive).")
    int endColumn;

    @Option(names = {"--name", "-n"}, required = true,
            description = "Name for the extracted method.")
    String methodName;

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

        String source   = Files.readString(absFile);
        int selStart    = JdtRenamer.toOffset(source, startLine, startColumn);
        int selEnd      = JdtRenamer.toOffset(source, endLine, endColumn);
        String result   = JdtExtractor.extractMethod(
                source, absFile.getFileName().toString(),
                selStart, selEnd - selStart, methodName);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no file written.");
            out.println();
            out.println(result);
        } else {
            Files.writeString(absFile, result);
            out.println("Extracted '" + methodName + "' in " + absFile.getFileName());
        }
        return 0;
    }
}
