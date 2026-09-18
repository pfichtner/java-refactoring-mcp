package dev.mcp.refactor.cli;

import dev.mcp.refactor.JdtIntroduceParam;
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

/** CLI subcommand for introduce-parameter refactoring. Logic lives in {@link JdtIntroduceParam}. */
@Command(
    name = "introduce-param",
    mixinStandardHelpOptions = true,
    description = "Promote an expression to a method parameter and update all call sites."
)
public class IntroduceParamCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the expression.")
    Path file;

    @Option(names = "--start-line",   required = true) int startLine;
    @Option(names = "--start-column", required = true) int startColumn;
    @Option(names = "--end-line",     required = true) int endLine;
    @Option(names = "--end-column",   required = true) int endColumn;
    @Option(names = {"--name", "-n"}, required = true,
            description = "Name for the new parameter.")
    String paramName;

    @Option(names = "--type", description = "Explicit type (inferred if omitted).")
    String paramType;

    @Option(names = "--project", description = "Maven project root (auto-detected if omitted).")
    Path projectRoot;

    @Option(names = "--dry-run") boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path absFile = file.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absFile)) {
            spec.commandLine().getErr().println("Error: file not found: " + absFile);
            return 1;
        }
        String source  = Files.readString(absFile);
        int selStart   = JdtRenamer.toOffset(source, startLine, startColumn);
        int selEnd     = JdtRenamer.toOffset(source, endLine, endColumn);

        Path root = projectRoot != null
                ? projectRoot.toAbsolutePath().normalize()
                : RenameCommand.findProjectRoot(absFile);

        Map<Path, String> changed = JdtIntroduceParam.introduceParam(
                new MavenProject(root), absFile, selStart, selEnd - selStart,
                paramName, paramType);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            changed.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> { out.println("\n=== " + e.getKey().getFileName() + " ==="); out.println(e.getValue().stripTrailing()); });
        } else {
            for (var entry : changed.entrySet()) Files.writeString(entry.getKey(), entry.getValue());
            out.println("Introduced parameter '" + paramName + "' in "
                    + changed.size() + " file(s):");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
