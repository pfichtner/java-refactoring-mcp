package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtRenamePackage;
import com.github.pfichtner.refactoring.project.JavaProject;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** CLI subcommand rename-package. Logic lives in {@link JdtRenamePackage}. */
@Command(
    name = "rename-package",
    mixinStandardHelpOptions = true,
    description = "Rename package across project, updating declarations and imports."
)
public class RenamePackageCommand implements Callable<Integer> {

    @Spec CommandSpec spec;

    @Option(names = "--old-package", required = true,
            description = "Fully-qualified old package, e.g. com.example.service.") String oldPackage;
    @Option(names = "--new-package", required = true,
            description = "Fully-qualified new package, e.g. com.example.util.") String newPackage;
    @Option(names = "--project",
            description = "Project root (Maven or Gradle). Defaults to current directory.")
    Path projectRoot;
    @Option(names = "--dry-run") boolean dryRun;

    @Override
    public Integer call() throws Exception {
        Path root = projectRoot != null
                ? projectRoot.toAbsolutePath().normalize()
                : Path.of(System.getProperty("user.dir"));

        JavaProject project = ProjectDetector.detect(root);
        JdtRenamePackage.Result result = JdtRenamePackage.renamePackage(project, oldPackage, newPackage);

        var out = spec.commandLine().getOut();
        if (dryRun) {
            out.println("Dry run — no files written.");
            for (FileChange fc : result.changedFiles()) {
                if (fc.pathChanged()) out.println(" MOVE " + fc.oldPath().getFileName() + " → " + fc.newPath());
                else out.println(" UPDATE " + fc.oldPath().getFileName());
                out.println(fc.newSource().stripTrailing());
                out.println();
            }
        } else {
            for (FileChange fc : result.changedFiles()) {
                Files.createDirectories(fc.newPath().getParent());
                Files.writeString(fc.newPath(), fc.newSource());
                if (fc.pathChanged()) Files.deleteIfExists(fc.oldPath());
            }
            out.println("Renamed package in " + result.changedFiles().size() + " file(s):");
            result.changedFiles().forEach(fc -> {
                if (fc.pathChanged()) out.println("  MOVED: " + fc.oldPath().getFileName() + " → " + fc.newPath().getFileName());
                else out.println("  UPDATED: " + fc.oldPath().getFileName());
            });
        }
        return 0;
    }
}
