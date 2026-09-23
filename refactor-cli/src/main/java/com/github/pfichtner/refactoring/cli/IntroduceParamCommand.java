package com.github.pfichtner.refactoring.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtIntroduceParam;
import com.github.pfichtner.refactoring.JdtRenamer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for introduce-parameter refactoring. Logic lives in {@link JdtIntroduceParam}. */
@Command(
    name = "introduce-param",
    mixinStandardHelpOptions = true,
    description = "Promote an expression to a method parameter and update all call sites."
)
public class IntroduceParamCommand extends AbstractProjectCommand {

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

    private Path absFile;

    @Override
    protected int validate() {
        absFile = checkedFile(file);
        return absFile == null ? 1 : 0;
    }

    @Override
    protected List<FileChange> refactor() throws Exception {
        String source  = Files.readString(absFile);
        int selStart   = JdtRenamer.toOffset(source, startLine, startColumn);
        int selEnd     = JdtRenamer.toOffset(source, endLine, endColumn);
        return toChanges(JdtIntroduceParam.introduceParam(
                project.resolve(absFile), absFile, selStart, selEnd - selStart, paramName, paramType));
    }

    @Override protected String successLine(int count) {
        return "Introduced parameter '" + paramName + "' in " + count + " file(s)";
    }
}