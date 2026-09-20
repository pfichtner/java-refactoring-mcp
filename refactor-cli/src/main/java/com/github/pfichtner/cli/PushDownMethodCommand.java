package com.github.pfichtner.cli;

import com.github.pfichtner.JdtPushDownMethod;
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

/** CLI subcommand for push-down-method refactoring. Logic lives in {@link JdtPushDownMethod}. */
@Command(
    name = "push-down",
    mixinStandardHelpOptions = true,
    description = "Move a method from a class down into all direct subclasses in the project."
)
public class PushDownMethodCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the superclass.") Path file;
    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line of the method to push down.") int line;
    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column of the method name.") int column;
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
        int offset    = JdtRenamer.toOffset(source, line, column);

        Map<Path, String> changed = JdtPushDownMethod.pushDown(project.resolve(absFile), absFile, offset);

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
            out.println("Pushed down method into " + changed.size() + " file(s):");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
