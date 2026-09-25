package com.github.pfichtner.refactoring.cli;

import java.nio.file.Path;

import com.github.pfichtner.refactoring.JdtConvertToStaticImport;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/** CLI subcommand for convert-to-static-import refactoring. */
@Command(name = "convert-to-static-import", mixinStandardHelpOptions = true,
         description = "Convert a qualified static-method call to use a static import.")
public class ConvertToStaticImportCommand extends SingleFileCommand {

    @Mixin LocatorOptions locator;

    @Option(names = "--replace-all",
            description = "Remove qualifier from every qualifying call to the same method (default: only this call).")
    boolean replaceAll;

    @Override protected int resolveOffset(String source, String unitName) {
        return locator.resolveOffset(source, unitName);
    }

    @Override protected String transform(String source, String unitName, int offset) throws Exception {
        return JdtConvertToStaticImport.convertToStaticImport(source, unitName, offset, replaceAll);
    }

    @Override protected String successMessage(Path f) {
        return "Converted to static import in " + f.getFileName();
    }
}
