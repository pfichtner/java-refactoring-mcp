package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.github.pfichtner.refactoring.JdtConvertNestedToTopLevel;
import com.github.pfichtner.refactoring.JdtRenamer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * CLI subcommand for converting a nested type to a top-level type.
 */
@Command(
    name = "convert-nested",
    mixinStandardHelpOptions = true,
    description = "Convert a nested (member) type to a top-level class in its own file."
)
public class ConvertNestedCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the nested type.")
    Path file;

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number inside the nested type.")
    int line;

    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number inside the nested type.")
    int column;

    @Option(names = "--output-dir",
            description = "Directory to write the new top-level file (defaults to same directory as source).")
    Path outputDir;

    @Option(names = "--dry-run",
            description = "Print both modified and new source without writing to disk.")
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
        JdtConvertNestedToTopLevel.Result result =
                JdtConvertNestedToTopLevel.convert(source, absFile.getFileName().toString(), offset);

        Path targetDir = outputDir != null ? outputDir.toAbsolutePath().normalize()
                                           : absFile.getParent();
        Path newFile   = targetDir.resolve(result.newTypeName() + ".java");

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            out.println();
            out.println("=== " + absFile.getFileName() + " (modified) ===");
            out.println(result.outerSource());
            out.println("=== " + newFile.getFileName() + " (new) ===");
            out.println(result.newTypeSource());
        } else {
            Files.writeString(absFile, result.outerSource());
            Files.writeString(newFile, result.newTypeSource());
            out.println("Moved '" + result.newTypeName() + "' to " + newFile);
            out.println("Updated " + absFile.getFileName());
        }
        return 0;
    }
}
