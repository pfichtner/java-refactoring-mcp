package com.github.pfichtner.cli;

import com.github.pfichtner.JdtRemoveParam;
import com.github.pfichtner.JdtRenamer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI subcommand for remove-parameter refactoring. Logic lives in {@link JdtRemoveParam}. */
@Command(
    name = "remove-param",
    mixinStandardHelpOptions = true,
    description = "Remove an unused parameter from a method and update all call sites."
)
public class RemoveParamCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Option(names = {"--line", "-l"}, required = true) int line;
    @Option(names = {"--column", "-c"}, required = true) int column;
    @Option(names = "--dry-run") boolean dryRun;

    @Mixin ProjectOptions project;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source = Files.readString(absFile);
        int offset    = JdtRenamer.toOffset(source, line, column);

        Map<Path, String> changed = JdtRemoveParam.removeParam(project.resolve(absFile), absFile, offset);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            changed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> { out.println("\n=== " + e.getKey().getFileName() + " ==="); out.println(e.getValue().stripTrailing()); });
        } else {
            for (var entry : changed.entrySet()) Files.writeString(entry.getKey(), entry.getValue());
            out.println("Removed parameter in " + changed.size() + " file(s):");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
