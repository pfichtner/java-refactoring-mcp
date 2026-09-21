package com.github.pfichtner.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import com.github.pfichtner.JdtExtractSuperclass;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** CLI subcommand for extract-superclass. Logic lives in {@link JdtExtractSuperclass}. */
@Command(
    name = "extract-superclass",
    mixinStandardHelpOptions = true,
    description = "Move methods into a new abstract superclass and make the class extend it."
)
public class ExtractSuperclassCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Option(names = {"--name", "-n"}, required = true,
            description = "Name for the new abstract superclass.") String superclassName;
    @Option(names = "--superclass-file", required = true,
            description = "Output path for the new superclass file.") Path superclassFile;
    @Option(names = "--methods", split = ",",
            description = "Methods to move (default: all public non-static).") List<String> methods = List.of();
    @Option(names = "--dry-run") boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source = Files.readString(absFile);
        JdtExtractSuperclass.Result result = JdtExtractSuperclass.extractSuperclass(
                source, absFile.getFileName().toString(), superclassName, methods);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            out.println("\n=== " + absFile.getFileName() + " (modified) ===");
            out.println(result.modifiedClassSource().stripTrailing());
            out.println("\n=== " + superclassFile.getFileName() + " (new) ===");
            out.println(result.superclassSource().stripTrailing());
        } else {
            Files.writeString(absFile, result.modifiedClassSource());
            Files.createDirectories(superclassFile.toAbsolutePath().getParent());
            Files.writeString(superclassFile.toAbsolutePath(), result.superclassSource());
            out.println("Extracted superclass '" + superclassName + "':");
            out.println("  Modified: " + absFile.getFileName());
            out.println("  Created:  " + superclassFile.getFileName());
        }
        return 0;
    }
}
