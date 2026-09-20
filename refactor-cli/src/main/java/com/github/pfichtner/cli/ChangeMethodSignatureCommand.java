package com.github.pfichtner.cli;

import com.github.pfichtner.JdtChangeMethodSignature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI subcommand for change-method-signature refactoring. Logic lives in {@link JdtChangeMethodSignature}. */
@Command(
    name = "change-method-signature",
    mixinStandardHelpOptions = true,
    description = "Change a method's return type and/or reorder its parameters. " +
                  "Call sites are updated when parameters are reordered."
)
public class ChangeMethodSignatureCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the method declaration.") Path file;
    @Option(names = "--dry-run",
            description = "Print changed sources; do not write to disk.") boolean dryRun;
    @Option(names = "--return-type",
            description = "New return type (e.g. 'double', 'List<String>'). Omit to leave unchanged.") String newReturnType;
    @Option(names = "--param-order", split = ",",
            description = "Comma-separated 0-based parameter indices giving the new order " +
                          "(e.g. '1,0' to swap two params). Omit to leave unchanged.") int[] paramOrder;

    @Mixin LocatorOptions locator;
    @Mixin ProjectOptions project;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source = Files.readString(absFile);
        int offset = locator.resolveOffset(source, absFile.getFileName().toString());

        Map<Path, String> changed = JdtChangeMethodSignature.changeSignature(
                project.resolve(absFile), absFile, offset, newReturnType, paramOrder);

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
            out.println("Signature changed; " + changed.size() + " file(s) updated:");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
