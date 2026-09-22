package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtIntroduceStaticFactory;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for introduce-static-factory refactoring. Logic lives in {@link JdtIntroduceStaticFactory}. */
@Command(name = "introduce-factory", mixinStandardHelpOptions = true,
         description = "Introduce a static factory method for a constructor and rewrite call sites.")
public class IntroduceStaticFactoryCommand extends ProjectWideLocatorCommand {

    @Option(names = {"--name", "-n"}, required = true,
            description = "Simple name for the factory method (e.g. 'of', 'create').")
    String factoryName;
    @Option(names = "--private-constructor",
            description = "Make the original constructor private.")
    boolean makePrivate;

    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        return JdtIntroduceStaticFactory.introduceStaticFactory(p, f, offset, factoryName, makePrivate);
    }
    @Override protected String successLine(int n) { return "Introduced factory method in " + n + " file(s):"; }
}
