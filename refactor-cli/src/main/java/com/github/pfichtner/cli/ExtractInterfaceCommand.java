package com.github.pfichtner.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import com.github.pfichtner.JdtExtractInterface;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** CLI subcommand for extract-interface refactoring. Logic lives in {@link JdtExtractInterface}. */
@Command(
    name = "extract-interface",
    mixinStandardHelpOptions = true,
    description = "Extract a new interface from the public methods of a class."
)
public class ExtractInterfaceCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the class.")
    Path file;

    @Option(names = {"--name", "-n"}, required = true,
            description = "Simple name for the new interface.")
    String interfaceName;

    @Option(names = "--interface-file", required = true,
            description = "Output path for the new interface file.")
    Path interfaceFile;

    @Option(names = "--methods", split = ",",
            description = "Comma-separated method names to include (default: all public non-static).")
    List<String> methods = List.of();

    @Option(names = "--dry-run") boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source = Files.readString(absFile);
        JdtExtractInterface.Result result = JdtExtractInterface.extractInterface(
                source, absFile.getFileName().toString(), interfaceName, methods);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            out.println("\n=== " + absFile.getFileName() + " (modified) ===");
            out.println(result.modifiedClassSource().stripTrailing());
            out.println("\n=== " + interfaceFile.getFileName() + " (new) ===");
            out.println(result.interfaceSource().stripTrailing());
        } else {
            Files.writeString(absFile, result.modifiedClassSource());
            Files.createDirectories(interfaceFile.toAbsolutePath().getParent());
            Files.writeString(interfaceFile.toAbsolutePath(), result.interfaceSource());
            out.println("Extracted interface '" + interfaceName + "':");
            out.println("  Modified: " + absFile.getFileName());
            out.println("  Created:  " + interfaceFile.getFileName());
        }
        return 0;
    }
}
