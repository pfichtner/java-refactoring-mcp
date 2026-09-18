package dev.mcp.refactor.cli;

import dev.mcp.refactor.JdtExtractConstant;
import dev.mcp.refactor.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** CLI subcommand for extract-constant refactoring. Logic lives in {@link JdtExtractConstant}. */
@Command(
    name = "extract-const",
    mixinStandardHelpOptions = true,
    description = "Extract an expression into a private static final constant."
)
public class ExtractConstCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Option(names = "--start-line",   required = true) int startLine;
    @Option(names = "--start-column", required = true) int startColumn;
    @Option(names = "--end-line",     required = true) int endLine;
    @Option(names = "--end-column",   required = true) int endColumn;
    @Option(names = {"--name", "-n"}, required = true) String constName;
    @Option(names = "--replace-all") boolean replaceAll;
    @Option(names = "--dry-run")     boolean dryRun;

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
        String result  = JdtExtractConstant.extractConstant(
                source, absFile.getFileName().toString(),
                selStart, selEnd - selStart, constName, replaceAll);

        var out = spec.commandLine().getOut();
        if (dryRun) { out.println("Dry run — no file written.\n"); out.println(result); }
        else        { Files.writeString(absFile, result); out.println("Extracted constant '" + constName + "' in " + absFile.getFileName()); }
        return 0;
    }
}
