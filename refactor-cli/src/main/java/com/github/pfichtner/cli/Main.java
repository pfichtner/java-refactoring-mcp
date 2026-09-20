package com.github.pfichtner.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "java-refactor",
    subcommands = {RenameCommand.class, ExtractCommand.class, InlineVarCommand.class, InlineConstantCommand.class, ExtractVarCommand.class, InlineMethodCommand.class, ExtractConstCommand.class, IntroduceParamCommand.class, RemoveParamCommand.class, ExtractInterfaceCommand.class, ExtractSuperclassCommand.class, MoveClassCommand.class, RenamePackageCommand.class, PullUpMethodCommand.class, PushDownMethodCommand.class, MoveMethodCommand.class, PullUpFieldCommand.class, PushDownFieldCommand.class, IntroduceStaticFactoryCommand.class, IntroduceParamObjectCommand.class, ConvertToRecordCommand.class, ChangeMethodSignatureCommand.class, EncapsulateFieldCommand.class, DecomposeConditionalCommand.class, ConvertAnonymousCommand.class, ConvertNestedCommand.class},
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
