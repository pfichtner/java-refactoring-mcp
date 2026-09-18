package dev.mcp.refactor.cli;

import dev.mcp.refactor.JdtInlineMethod;
import dev.mcp.refactor.JdtRenamer;
import dev.mcp.refactor.project.MavenProject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI subcommand for inline-method refactoring. All logic lives in {@link JdtInlineMethod}. */
@Command(
    name = "inline-method",
    mixinStandardHelpOptions = true,
    description = "Inline a method call: replace it with the method body."
)
public class InlineMethodCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Option(names = {"--line", "-l"}, required = true) int line;
    @Option(names = {"--column", "-c"}, required = true) int column;
    @Option(names = "--project") Path projectRoot;
    @Option(names = "--remove-declaration",
            description = "Also delete the method declaration after inlining.") boolean removeDeclaration;
    @Option(names = "--dry-run") boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source = Files.readString(absFile);
        int offset    = JdtRenamer.toOffset(source, line, column);
        var out = spec.commandLine().getOut();

        Path root = projectRoot != null
                ? projectRoot.toAbsolutePath().normalize()
                : RenameCommand.findProjectRoot(absFile);

        Map<Path, String> changed = JdtInlineMethod.inlineMethod(
                new MavenProject(root), absFile, offset, removeDeclaration);

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
