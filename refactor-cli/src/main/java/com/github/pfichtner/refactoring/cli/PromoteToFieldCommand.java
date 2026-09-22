package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtPromoteToField;
import com.github.pfichtner.refactoring.JdtRenamer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for promoting a local variable to a field. */
@Command(name = "promote-to-field", mixinStandardHelpOptions = true,
         description = "Promote a local variable declaration to a private instance field.")
public class PromoteToFieldCommand extends SingleFileCommand {

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number inside the local variable declaration.")
    int line;
    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number inside the local variable declaration.")
    int column;

    @Override protected int resolveOffset(String source, String unitName) {
        return JdtRenamer.toOffset(source, line, column);
    }
    @Override protected String transform(String source, String unitName, int offset) throws Exception {
        return JdtPromoteToField.promote(source, unitName, offset);
    }
    @Override protected String successMessage(Path f) {
        return "Promoted local variable to field in " + f.getFileName();
    }
}
