package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.List;

import com.github.pfichtner.refactoring.FileChange;
import com.github.pfichtner.refactoring.JdtConvertToRecord;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for convert-to-record refactoring. Logic lives in {@link JdtConvertToRecord}. */
@Command(
    name = "convert-to-record",
    mixinStandardHelpOptions = true,
    description = "Convert a simple data class to a Java record and rename bean-style getter " +
                  "call sites across the project (requires Java 16+ to compile output)."
)
public class ConvertToRecordCommand extends AbstractProjectCommand {

    @Option(names = {"--file", "-f"}, required = true,
            description = "Source file containing the class to convert.")
    Path file;

    private Path absFile;

    @Override
    protected int validate() {
        absFile = checkedFile(file);
        return absFile == null ? 1 : 0;
    }

    @Override
    protected List<FileChange> refactor() throws Exception {
        return toChanges(JdtConvertToRecord.convertToRecord(project.resolve(absFile), absFile));
    }

    @Override protected String successLine(int count) { return "Converted to record; " + count + " file(s) changed"; }
}