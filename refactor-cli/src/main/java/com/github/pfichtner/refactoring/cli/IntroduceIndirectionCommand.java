package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtIntroduceIndirection;
import com.github.pfichtner.refactoring.JdtRenamer;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for introducing a static indirection wrapper method. */
@Command(name = "introduce-indirection", mixinStandardHelpOptions = true,
         description = "Add a static wrapper method that delegates to the method at the given position.")
public class IntroduceIndirectionCommand extends SingleFileCommand {

    @Option(names = {"--line", "-l"}, required = true,
            description = "1-based line number inside the method declaration.")
    int line;
    @Option(names = {"--column", "-c"}, required = true,
            description = "1-based column number inside the method declaration.")
    int column;
    @Option(names = {"--name", "-n"}, required = true,
            description = "Name for the new indirection (wrapper) method.")
    String indirectionName;

    @Override protected int resolveOffset(String source, String unitName) {
        return JdtRenamer.toOffset(source, line, column);
    }
    @Override protected String transform(String source, String unitName, int offset) throws Exception {
        return JdtIntroduceIndirection.introduceIndirection(source, unitName, offset, indirectionName);
    }
    @Override protected String successMessage(Path f) {
        return "Introduced indirection method '" + indirectionName + "' in " + f.getFileName();
    }
}
