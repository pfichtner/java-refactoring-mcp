package dev.mcp.refactor.cli;

import dev.mcp.refactor.JdtConvertToRecord;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** CLI subcommand for convert-to-record refactoring. Logic lives in {@link JdtConvertToRecord}. */
@Command(
    name = "convert-to-record",
    mixinStandardHelpOptions = true,
    description = "Convert a simple data class to a Java record (requires Java 16+ to compile output)."
)
public class ConvertToRecordCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the class to convert.") Path file;
    @Option(names = "--dry-run",
            description = "Print the converted source; do not write to disk.") boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }

        String source = Files.readString(absFile);
        String result = JdtConvertToRecord.convertToRecord(source, absFile.getFileName().toString());

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            out.println("\n=== " + absFile.getFileName() + " ===");
            out.println(result.stripTrailing());
        } else {
            Files.writeString(absFile, result);
            out.println("Converted to record: " + absFile.getFileName());
        }
        return 0;
    }
}
