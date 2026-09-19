package com.github.pfichtner.cli;

import com.github.pfichtner.JdtIntroduceParameterObject;
import com.github.pfichtner.JdtRenamer;
import com.github.pfichtner.project.MavenProject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/** CLI subcommand for introduce-parameter-object refactoring. Logic lives in {@link JdtIntroduceParameterObject}. */
@Command(
    name = "introduce-param-object",
    mixinStandardHelpOptions = true,
    description = "Group contiguous method parameters into a new value-object class."
)
public class IntroduceParamObjectCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the method.") Path file;
    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line of the method declaration.") int line;
    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column inside the method declaration.") int column;
    @Option(names = {"--params", "-p"}, required = true, split = ",",
            description = "Comma-separated parameter names to group (e.g. x,y).") List<String> params;
    @Option(names = {"--class-name", "-n"}, required = true,
            description = "Simple name for the new parameter-object class.") String className;
    @Option(names = "--param-name",
            description = "Name for the new parameter in the method (default: first letter of class name lower-cased).")
            String paramName;
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

        String resolvedParamName = paramName != null
                ? paramName
                : Character.toLowerCase(className.charAt(0)) + className.substring(1);

        String source = Files.readString(absFile);
        int offset    = JdtRenamer.toOffset(source, line, column);

        Map<Path, String> changed = JdtIntroduceParameterObject.introduce(
                new MavenProject(root), absFile, offset, params, className, resolvedParamName);

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
            out.println("Introduced parameter object in " + changed.size() + " file(s):");
            changed.keySet().stream().sorted().forEach(p -> out.println("  " + p.getFileName()));
        }
        return 0;
    }
}
