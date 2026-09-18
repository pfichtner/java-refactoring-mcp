package dev.mcp.refactor.cli;

import dev.mcp.refactor.JdtInlineMethod;
import dev.mcp.refactor.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** CLI subcommand for inline-method refactoring. All logic lives in {@link JdtInlineMethod}. */
@Command(
    name = "inline-method",
    mixinStandardHelpOptions = true,
    description = "Inline a method call: replace it with the method body."
)
public class InlineMethodCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Option(names = {"--line", "-l"}, required = true) int line;
    @Option(names = {"--column", "-c"}, required = true) int column;
    @Option(names = "--dry-run") boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source = Files.readString(absFile);
        int offset    = JdtRenamer.toOffset(source, line, column);
        String result = JdtInlineMethod.inlineMethod(source, absFile.getFileName().toString(), offset);

        var out = spec.commandLine().getOut();
        if (dryRun) { out.println("Dry run — no file written.\n"); out.println(result); }
        else        { Files.writeString(absFile, result); out.println("Inlined method in " + absFile.getFileName()); }
        return 0;
    }
}
