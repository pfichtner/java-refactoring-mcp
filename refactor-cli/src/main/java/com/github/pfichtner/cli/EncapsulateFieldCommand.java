package com.github.pfichtner.cli;

import com.github.pfichtner.JdtEncapsulateField;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI subcommand for encapsulate-field refactoring. Logic lives in {@link JdtEncapsulateField}. */
@Command(
    name = "encapsulate-field",
    mixinStandardHelpOptions = true,
    description = "Make a public field private, generate getter (and optional setter), " +
                  "and rewrite all access sites across the project."
)
public class EncapsulateFieldCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the field declaration.") Path file;
    @Option(names = "--dry-run",
            description = "Print changed sources; do not write to disk.") boolean dryRun;
    @Option(names = "--setter",
            description = "Also generate a setter and rewrite write access sites (default: false).")
    boolean generateSetter;

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

        Map<Path, String> changed = JdtEncapsulateField.encapsulateField(
                project.resolve(absFile), absFile, offset, generateSetter);

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
            out.println("Field encapsulated; " + changed.size() + " file(s) changed:");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
