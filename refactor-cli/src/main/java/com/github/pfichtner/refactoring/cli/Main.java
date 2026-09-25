package com.github.pfichtner.refactoring.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "java-refactor",
    versionProvider = Main.VersionProvider.class,
    subcommands = {RenameCommand.class, ExtractCommand.class, InlineVarCommand.class, InlineConstantCommand.class, ExtractVarCommand.class, InlineMethodCommand.class, ExtractConstCommand.class, IntroduceParamCommand.class, RemoveParamCommand.class, RemoveMethodCommand.class, ExtractInterfaceCommand.class, ExtractSuperclassCommand.class, MoveClassCommand.class, RenamePackageCommand.class, PullUpMethodCommand.class, PushDownMethodCommand.class, MoveMethodCommand.class, PullUpFieldCommand.class, PushDownFieldCommand.class, IntroduceStaticFactoryCommand.class, IntroduceParamObjectCommand.class, ConvertToRecordCommand.class, ChangeMethodSignatureCommand.class, EncapsulateFieldCommand.class, DecomposeConditionalCommand.class, ConvertAnonymousCommand.class, ConvertNestedCommand.class, PromoteToFieldCommand.class, MoveStaticMemberCommand.class, IntroduceIndirectionCommand.class, ConvertToStaticImportCommand.class},
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

    /** Reports the version from the JAR manifest, which is stamped from {@code ${project.version}}. */
    public static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = Main.class.getPackage().getImplementationVersion();
            return new String[] {version != null ? version : "unknown"};
        }
    }
}
