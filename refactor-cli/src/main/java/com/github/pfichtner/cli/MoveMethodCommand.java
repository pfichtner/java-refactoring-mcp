package com.github.pfichtner.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import com.github.pfichtner.JdtMoveMethod;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** CLI subcommand for move-method refactoring. Logic lives in {@link JdtMoveMethod}. */
@Command(
    name = "move-method",
    mixinStandardHelpOptions = true,
    description = "Move a method from one class to another class within the project."
)
public class MoveMethodCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the class with the method to move.") Path file;
    @Mixin LocatorOptions locator;
    @Option(names = {"--target-class", "-t"}, required = true,
            description = "Fully-qualified name of the target class (e.g. 'com.example.Report').") String targetClass;
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
        String source = Files.readString(absFile);
        int offset    = locator.resolveOffset(source, absFile.getFileName().toString());

        Map<Path, String> changed = JdtMoveMethod.moveMethod(project.resolve(absFile), absFile, offset, targetClass);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            out.println("Would change (" + changed.size() + "):");
            changed.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
                out.println("\n=== " + e.getKey().getFileName() + " ===");
                out.println(e.getValue().stripTrailing());
            });
        } else {
            for (Map.Entry<Path, String> e : changed.entrySet()) {
                Files.writeString(e.getKey(), e.getValue());
            }
            out.println("Moved method into " + changed.size() + " file(s):");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
