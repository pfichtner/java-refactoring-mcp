package com.github.pfichtner.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import com.github.pfichtner.JdtRemoveMethod;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** CLI subcommand for remove-method refactoring. Logic lives in {@link JdtRemoveMethod}. */
@Command(
    name = "remove-method",
    mixinStandardHelpOptions = true,
    description = "Remove a method from a class or interface, optionally cascading to overriding/implementing methods."
)
public class RemoveMethodCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "File containing the method to remove.")
    Path file;

    @Mixin LocatorOptions locator;

    @Option(names = "--no-cascade",
            description = "Skip removal in overriding and implementing methods (cascade is on by default).")
    boolean noCascade;

    @Option(names = "--dry-run",
            description = "Print changes without writing to disk.")
    boolean dryRun;

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

        Map<Path, String> changed = JdtRemoveMethod.removeMethod(
                project.resolve(absFile), absFile, offset, !noCascade);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            changed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> {
                        out.println("\n=== " + e.getKey().getFileName() + " ===");
                        out.println(e.getValue().stripTrailing());
                    });
        } else {
            for (var entry : changed.entrySet()) {
                Files.writeString(entry.getKey(), entry.getValue());
            }
            out.println("Removed method in " + changed.size() + " file(s):");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
