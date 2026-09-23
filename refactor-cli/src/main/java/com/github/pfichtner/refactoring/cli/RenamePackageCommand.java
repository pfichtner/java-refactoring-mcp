package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtRenamePackage;
import com.github.pfichtner.refactoring.project.JavaProject;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand rename-package. Logic lives in {@link JdtRenamePackage}. */
@Command(
    name = "rename-package",
    mixinStandardHelpOptions = true,
    description = "Rename package across project, updating declarations and imports."
)
public class RenamePackageCommand extends AbstractRefactoringCommand {

    @Option(names = "--old-package", required = true,
            description = "Fully-qualified old package, e.g. com.example.service.")
    String oldPackage;

    @Option(names = "--new-package", required = true,
            description = "Fully-qualified new package, e.g. com.example.util.")
    String newPackage;

    @Option(names = "--project",
            description = "Project root (Maven or Gradle). Defaults to current directory.")
    Path projectRoot;

    @Override
    protected List<FileChange> refactor() throws Exception {
        Path root = projectRoot != null
                ? projectRoot.toAbsolutePath().normalize()
                : Path.of(System.getProperty("user.dir"));
        JavaProject project = ProjectDetector.detect(root);
        return JdtRenamePackage.renamePackage(project, oldPackage, newPackage).changedFiles();
    }

    @Override protected String successLine(int count) { return "Renamed package in " + count + " file(s)"; }
}