package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtEncapsulateField;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for encapsulate-field refactoring. Logic lives in {@link JdtEncapsulateField}. */
@Command(name = "encapsulate-field", mixinStandardHelpOptions = true,
         description = "Make a public field private, generate getter (and optional setter), " +
                       "and rewrite all access sites across the project.")
public class EncapsulateFieldCommand extends ProjectWideLocatorCommand {

    @Option(names = "--setter",
            description = "Also generate a setter and rewrite write access sites (default: false).")
    boolean generateSetter;

    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtEncapsulateField.encapsulateField(p, f, offset, generateSetter);
    }
    @Override protected String successLine(int n) { return "Field encapsulated; " + n + " file(s) changed:"; }
}
