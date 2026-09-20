package com.github.pfichtner.cli;

import com.github.pfichtner.JdtIntroduceStaticFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI subcommand for introduce-static-factory refactoring. Logic lives in {@link JdtIntroduceStaticFactory}. */
@Command(
    name = "introduce-factory",
    mixinStandardHelpOptions = true,
    description = "Introduce a static factory method for a constructor and rewrite call sites."
)
public class IntroduceStaticFactoryCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the class.") Path file;
    @Mixin LocatorOptions locator;
    @Option(names = {"--name", "-n"}, required = true,
            description = "Simple name for the factory method (e.g. 'of', 'create').") String factoryName;
    @Option(names = "--private-constructor",
            description = "Make the original constructor private.") boolean makePrivate;
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

        Map<Path, String> changed = JdtIntroduceStaticFactory.introduceStaticFactory(
                project.resolve(absFile), absFile, offset, factoryName, makePrivate);

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
            out.println("Introduced factory method in " + changed.size() + " file(s):");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
