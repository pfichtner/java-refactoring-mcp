package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtConvertNestedToTopLevel;
import com.github.pfichtner.refactoring.JdtRenamer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * CLI subcommand for converting a nested type to a top-level type.
 */
@Command(
    name = "convert-nested",
    mixinStandardHelpOptions = true,
    description = "Convert a nested (member) type to a top-level class in its own file."
)
public class ConvertNestedCommand extends AbstractRefactoringCommand {

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the nested type.")
    Path file;

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number inside the nested type.")
    int line;

    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number inside the nested type.")
    int column;

    @Option(names = "--output-dir",
            description = "Directory to write the new top-level file (defaults to same directory as source).")
    Path outputDir;

    private Path absFile;
    private Path newFile;
    private String newTypeName;

    @Override
    protected int validate() {
        absFile = checkedFile(file);
        return absFile == null ? 1 : 0;
    }

    @Override
    protected List<FileChange> refactor() throws Exception {
        String source = Files.readString(absFile);
        int offset    = JdtRenamer.toOffset(source, line, column);
        JdtConvertNestedToTopLevel.Result result =
                JdtConvertNestedToTopLevel.convert(source, absFile.getFileName().toString(), offset);

        Path targetDir = outputDir != null ? outputDir.toAbsolutePath().normalize()
                                           : absFile.getParent();
        newTypeName = result.newTypeName();
        newFile = targetDir.resolve(newTypeName + ".java");
        return List.of(
                new FileChange(absFile, absFile, result.outerSource()),
                new FileChange(newFile, newFile, result.newTypeSource()));
    }

    @Override
    protected void printApplyReport(List<FileChange> changes) {
        var out = spec.commandLine().getOut();
        out.println("Moved '" + newTypeName + "' to " + newFile);
        out.println("Updated " + absFile.getFileName());
    }
}