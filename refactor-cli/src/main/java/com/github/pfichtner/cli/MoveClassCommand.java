package com.github.pfichtner.cli;

import com.github.pfichtner.JdtMoveClass;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI subcommand for move-class refactoring. Logic lives in {@link JdtMoveClass}. */
@Command(
    name = "move-class",
    mixinStandardHelpOptions = true,
    description = "Move a class to a new package, updating all imports in the project."
)
public class MoveClassCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Option(names = {"--package", "-p"}, required = true,
            description = "Target package, e.g. com.example.util") String targetPackage;
    @Option(names = "--dry-run") boolean dryRun;

    @Mixin ProjectOptions project;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        JdtMoveClass.Result result = JdtMoveClass.moveClass(
                project.resolve(absFile), absFile, targetPackage);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            out.println("New path: " + result.newFilePath());
            out.println("\n=== " + result.newFilePath().getFileName() + " (new) ===");
            out.println(result.newClassSource().stripTrailing());
            for (Map.Entry<Path, String> e : result.changedImports().entrySet()) {
                out.println("\n=== " + e.getKey().getFileName() + " (updated import) ===");
                out.println(e.getValue().stripTrailing());
            }
        } else {
            // Write new file, update imports, delete old file
            Files.createDirectories(result.newFilePath().getParent());
            Files.writeString(result.newFilePath(), result.newClassSource());
            for (Map.Entry<Path, String> e : result.changedImports().entrySet()) {
                Files.writeString(e.getKey(), e.getValue());
            }
            Files.delete(absFile);
            out.println("Moved " + absFile.getFileName() + " → " + result.newFilePath());
            if (!result.changedImports().isEmpty()) {
                out.println("Updated imports in " + result.changedImports().size() + " file(s):");
                result.changedImports().keySet().stream().sorted()
                        .forEach(p -> out.println("  " + p.getFileName()));
            }
        }
        return 0;
    }
}
