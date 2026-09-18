package dev.mcp.refactor.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "java-refactor",
    subcommands = {RenameCommand.class, ExtractCommand.class, InlineVarCommand.class, ExtractVarCommand.class, InlineMethodCommand.class, ExtractConstCommand.class, IntroduceParamCommand.class, RemoveParamCommand.class, ExtractInterfaceCommand.class, ExtractSuperclassCommand.class, MoveClassCommand.class},
    mixinStandardHelpOptions = true,
    description = "Headless semantic Java refactoring tool powered by Eclipse JDT."
)
public class Main {

    public static void main(String[] args) {
        int exit = new CommandLine(new Main())
            .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                cmd.getErr().println("Error: " + ex.getMessage());
                return 1;
            })
            .execute(args);
        System.exit(exit);
    }
}
