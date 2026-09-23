package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtMoveClass;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for move-class refactoring. Logic lives in {@link JdtMoveClass}. */
@Command(
    name = "move-class",
    mixinStandardHelpOptions = true,
    description = "Move a class to a new package, updating all imports in the project."
)
public class MoveClassCommand extends AbstractProjectCommand {

    @Option(names = {"--file", "-f"}, required = true) Path file;
    @Option(names = {"--package", "-p"}, required = true,
            description = "Target package, e.g. com.example.util") String targetPackage;

    private Path absFile;

    @Override
    protected int validate() {
        absFile = checkedFile(file);
        return absFile == null ? 1 : 0;
    }

    @Override
    protected List<FileChange> refactor() throws Exception {
        JdtMoveClass.Result result = JdtMoveClass.moveClass(
                project.resolve(absFile), absFile, targetPackage);
        List<FileChange> changes = new ArrayList<>();
        changes.add(new FileChange(absFile, result.newFilePath(), result.newClassSource()));
        for (Map.Entry<Path, String> e : result.changedImports().entrySet()) {
            changes.add(new FileChange(e.getKey(), e.getKey(), e.getValue()));
        }
        return changes;
    }

    @Override protected String successLine(int count) { return "Moved class; " + count + " file(s) changed"; }
}