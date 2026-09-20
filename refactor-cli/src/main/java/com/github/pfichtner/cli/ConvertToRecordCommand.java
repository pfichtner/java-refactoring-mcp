package com.github.pfichtner.cli;

import com.github.pfichtner.JdtConvertToRecord;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI subcommand for convert-to-record refactoring. Logic lives in {@link JdtConvertToRecord}. */
@Command(
    name = "convert-to-record",
    mixinStandardHelpOptions = true,
    description = "Convert a simple data class to a Java record and rename bean-style getter " +
                  "call sites across the project (requires Java 16+ to compile output)."
)
public class ConvertToRecordCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the class to convert.") Path file;
    @Option(names = "--dry-run",
            description = "Print changed sources; do not write to disk.") boolean dryRun;

    @Mixin ProjectOptions project;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        Map<Path, String> changed = JdtConvertToRecord.convertToRecord(
                project.resolve(absFile), absFile);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            out.println("Changed files (" + changed.size() + "):");
            changed.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                out.println("\n=== " + e.getKey().getFileName() + " ===");
                out.println(e.getValue().stripTrailing());
            });
        } else {
            for (Map.Entry<Path, String> e : changed.entrySet()) {
                Files.writeString(e.getKey(), e.getValue());
            }
            out.println("Converted to record; " + changed.size() + " file(s) changed:");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
