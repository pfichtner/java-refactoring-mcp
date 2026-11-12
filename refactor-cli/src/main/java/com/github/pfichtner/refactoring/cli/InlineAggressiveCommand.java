package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.inlining.InlineConfig;
import com.github.pfichtner.refactoring.inlining.InliningEngine;
import com.github.pfichtner.refactoring.inlining.ProjectInliningResult;
import com.github.pfichtner.refactoring.project.JavaProject;
import com.github.pfichtner.refactoring.project.ProjectDetector;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for aggressive project-wide inlining. All logic lives in {@link InliningEngine}. */
@Command(name = "inline-aggressive", mixinStandardHelpOptions = true,
         description = "Aggressively inline all inlineable calls project-wide to a fixed point.")
public class InlineAggressiveCommand extends AbstractRefactoringCommand {

    @Option(names = "--project",
            description = "Project root (Maven or Gradle). Defaults to current directory.")
    Path projectRoot;

    @Option(names = "--max-transformations", defaultValue = "100",
            description = "Max number of inline transformations (default: 100).")
    int maxTransformations;

    @Option(names = "--max-code-growth", defaultValue = "100000",
            description = "Max code growth in characters (default: 100 000).")
    int maxCodeGrowth;

    @Option(names = "--remove-unused-declarations",
            description = "Remove method declarations with no remaining call sites after inlining.")
    boolean removeUnusedDeclarations;

    private int appliedCount;
    private boolean fixedPoint;

    @Override
    protected List<FileChange> refactor() throws Exception {
        Path root = projectRoot != null
                ? projectRoot.toAbsolutePath().normalize()
                : Path.of(System.getProperty("user.dir"));
        JavaProject project = ProjectDetector.detect(root);
        InlineConfig config = new InlineConfig(maxTransformations, maxCodeGrowth, removeUnusedDeclarations);
        ProjectInliningResult result = InliningEngine.inlineProject(project, config);
        appliedCount = result.transformationCount();
        fixedPoint = result.fixedPoint();
        return result.changedFiles().entrySet().stream()
                .map(e -> new FileChange(e.getKey(), e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(fc -> fc.newPath().toString()))
                .toList();
    }

    @Override
    protected String successLine(int count) {
        return "Aggressively inlined " + count + " file(s) (" + appliedCount
                + " transformation(s), " + (fixedPoint ? "fixed point" : "budget exhausted") + "):";
    }
}
