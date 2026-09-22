package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.github.pfichtner.refactoring.JdtIntroduceParameterObject;
import com.github.pfichtner.refactoring.project.JavaProject;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/** CLI subcommand for introduce-parameter-object refactoring. Logic lives in {@link JdtIntroduceParameterObject}. */
@Command(name = "introduce-param-object", mixinStandardHelpOptions = true,
         description = "Group contiguous method parameters into a new value-object class.")
public class IntroduceParamObjectCommand extends ProjectWideLocatorCommand {

    @Option(names = {"--params", "-p"}, required = true, split = ",",
            description = "Comma-separated parameter names to group (e.g. x,y).") List<String> params;
    @Option(names = {"--class-name", "-n"}, required = true,
            description = "Simple name for the new parameter-object class.") String className;
    @Option(names = "--param-name",
            description = "Name for the new parameter in the method (default: lower-camel of class name).")
    String paramName;
    @Option(names = "--record",
            description = "Generate a record instead of a plain class (Java 16+).") boolean asRecord;

    @Override protected Map<Path, String> execute(JavaProject p, Path f, int offset) throws Exception {
        String resolvedName = paramName != null
                ? paramName
                : Character.toLowerCase(className.charAt(0)) + className.substring(1);
        return JdtIntroduceParameterObject.introduce(p, f, offset, params, className, resolvedName, asRecord);
    }
    @Override protected String successLine(int n) { return "Introduced parameter object in " + n + " file(s):"; }
}
