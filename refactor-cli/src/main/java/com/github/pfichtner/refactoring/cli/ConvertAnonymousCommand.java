package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtConvertAnonymousToNested;
import com.github.pfichtner.refactoring.JdtRenamer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for converting an anonymous class to a named nested class. */
@Command(name = "convert-anonymous", mixinStandardHelpOptions = true,
         description = "Convert an anonymous class to a named nested class.")
public class ConvertAnonymousCommand extends SingleFileCommand {

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number inside the anonymous class.")
    int line;
    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number inside the anonymous class.")
    int column;
    @Option(names = {"--name", "-n"}, required = true,
            description = "Simple name for the new nested class.")
    String nestedClassName;

    @Override protected int resolveOffset(String source, String unitName) {
        return JdtRenamer.toOffset(source, line, column);
    }
    @Override protected String transform(String source, String unitName, int offset) throws Exception {
        return JdtConvertAnonymousToNested.convert(source, unitName, offset, nestedClassName);
    }
    @Override protected String successMessage(Path f) {
        return "Converted anonymous class to nested class '" + nestedClassName + "' in " + f.getFileName();
    }
}
