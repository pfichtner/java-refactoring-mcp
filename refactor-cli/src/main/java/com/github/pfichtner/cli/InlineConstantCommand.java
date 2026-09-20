package com.github.pfichtner.cli;

import com.github.pfichtner.JdtInliner;
import com.github.pfichtner.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** CLI subcommand for inline-constant refactoring. All logic lives in {@link JdtInliner}. */
@Command(
    name = "inline-const",
    mixinStandardHelpOptions = true,
    description = "Inline a static final constant: replace references with its initializer."
)
public class InlineConstantCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the constant.")
    Path file;

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number of the constant reference or declaration.")
    int line;

    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number of the constant name.")
    int column;

    @Option(names = "--all-occurrences",
            description = "Replace all references in the file (default: only this reference).")
    boolean allOccurrences;

    @Option(names = "--remove-declaration",
            description = "Also delete the constant declaration (requires --all-occurrences).")
    boolean removeDeclaration;

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
        String result = JdtInliner.inlineConstant(
                source, absFile.getFileName().toString(), offset, allOccurrences, removeDeclaration);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no file written.");
            out.println();
            out.println(result);
        } else {
            Files.writeString(absFile, result);
            out.println("Inlined constant in " + absFile.getFileName());
        }
        return 0;
    }
}
