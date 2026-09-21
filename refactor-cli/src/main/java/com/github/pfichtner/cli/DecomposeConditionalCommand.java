package com.github.pfichtner.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.github.pfichtner.JdtDecomposeConditional;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** CLI subcommand for decompose-conditional refactoring. Logic lives in {@link JdtDecomposeConditional}. */
@Command(
    name = "decompose-conditional",
    mixinStandardHelpOptions = true,
    description = "Extract a compound boolean condition from an if/while/for statement " +
                  "into a private boolean method with a meaningful name."
)
public class DecomposeConditionalCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the conditional statement.") Path file;
    @Option(names = "--name", required = true,
            description = "Name for the extracted boolean method (e.g. 'isAdultPremium').") String methodName;
    @Option(names = "--dry-run",
            description = "Print changed source; do not write to disk.") boolean dryRun;

    @Mixin LocatorOptions locator;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source = Files.readString(absFile);
        int offset = locator.resolveOffset(source, absFile.getFileName().toString());

        String result = JdtDecomposeConditional.decomposeConditional(source, absFile.getFileName().toString(), offset, methodName);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no file written.");
            out.println("\n=== " + absFile.getFileName() + " ===");
            out.println(result.stripTrailing());
        } else {
            Files.writeString(absFile, result);
            out.println("Conditional decomposed: " + absFile.getFileName());
        }
        return 0;
    }
}
