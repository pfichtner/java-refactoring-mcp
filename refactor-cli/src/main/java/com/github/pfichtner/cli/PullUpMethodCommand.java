package com.github.pfichtner.cli;

import com.github.pfichtner.JdtPullUpMethod;
import com.github.pfichtner.JdtRenamer;
import com.github.pfichtner.project.MavenProject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI subcommand for pull-up-method refactoring. Logic lives in {@link JdtPullUpMethod}. */
@Command(
    name = "pull-up",
    mixinStandardHelpOptions = true,
    description = "Move a method from a subclass up to its direct superclass."
)
public class PullUpMethodCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the subclass.") Path file;
    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line of the method to pull up.") int line;
    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column of the method name.") int column;
    @Option(names = "--project",
            description = "Maven project root (auto-detected if omitted).") Path projectRoot;
    @Option(names = "--dry-run",
            description = "Print changed sources; do not write to disk.") boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        Path root = projectRoot != null
                ? projectRoot.toAbsolutePath().normalize()
                : RenameCommand.findProjectRoot(absFile);

        String source = Files.readString(absFile);
        int offset    = JdtRenamer.toOffset(source, line, column);

        Map<Path, String> changed = JdtPullUpMethod.pullUp(new MavenProject(root), absFile, offset);

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
            out.println("Pulled up method into " + changed.size() + " file(s):");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
