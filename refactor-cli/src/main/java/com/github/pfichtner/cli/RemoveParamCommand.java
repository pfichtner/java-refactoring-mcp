package com.github.pfichtner.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import com.github.pfichtner.JdtRemoveParam;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** CLI subcommand for remove-parameter refactoring. Logic lives in {@link JdtRemoveParam}. */
@Command(
    name = "remove-param",
    mixinStandardHelpOptions = true,
    description = "Remove an unused parameter from a method and update all call sites."
)
public class RemoveParamCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Mixin LocatorOptions locator;
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
        int offset    = locator.resolveOffset(source, absFile.getFileName().toString());

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
