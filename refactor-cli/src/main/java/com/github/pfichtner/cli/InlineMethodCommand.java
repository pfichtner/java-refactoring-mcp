package com.github.pfichtner.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

import com.github.pfichtner.JdtInlineMethod;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** CLI subcommand for inline-method refactoring. All logic lives in {@link JdtInlineMethod}. */
@Command(
    name = "inline-method",
    mixinStandardHelpOptions = true,
    description = "Inline a method call: replace it with the method body."
)
public class InlineMethodCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Mixin LocatorOptions locator;
    @Option(names = "--remove-declaration",
            description = "Also delete the method declaration after inlining.") boolean removeDeclaration;
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
        var out = spec.commandLine().getOut();

        Map<Path, String> changed = JdtInlineMethod.inlineMethod(
                project.resolve(absFile), absFile, offset, removeDeclaration);

        if (dryRun) {
            out.println("Dry run — no files written.");
            changed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> { out.println("\n=== " + e.getKey().getFileName() + " ==="); out.println(e.getValue().stripTrailing()); });
        } else {
            for (var entry : changed.entrySet()) Files.writeString(entry.getKey(), entry.getValue());
            out.println("Inlined method in " + changed.size() + " file(s):");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
