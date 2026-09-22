package com.github.pfichtner.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.io.TempDir;

import picocli.CommandLine;

class CliTestBedExtension implements ParameterResolver {

    private static final Namespace NS = Namespace.create(CliTestBedExtension.class);

    @Override
    public boolean supportsParameter(ParameterContext param, ExtensionContext ctx) {
        Class<?> type = param.getParameter().getType();
        return type == CommandLine.class
                || type == StringWriter.class
                || (type == Path.class && !param.isAnnotated(TempDir.class));
    }

    @Override
    public Object resolveParameter(ParameterContext param, ExtensionContext ctx) {
        Store store = ctx.getStore(NS);
        Class<?> type = param.getParameter().getType();

        if (type == StringWriter.class) return getOrCreateOut(store);
        if (type == CommandLine.class) return getOrCreateCli(store);
        if (type == Path.class) return resolveRoot(ctx);

        throw new ParameterResolutionException("Unsupported type: " + type);
    }

    private StringWriter getOrCreateOut(Store store) {
        return store.getOrComputeIfAbsent("out", k -> new StringWriter(), StringWriter.class);
    }

    private CommandLine getOrCreateCli(Store store) {
        StringWriter out = getOrCreateOut(store);
        return store.getOrComputeIfAbsent("cli", k -> buildCli(out), CommandLine.class);
    }

    private static Path resolveRoot(ExtensionContext ctx) {
        CliTestBed annotation = ctx.getRequiredTestClass().getAnnotation(CliTestBed.class);
        if (annotation == null) {
            throw new ParameterResolutionException(
                    "@CliTestBed not found on " + ctx.getRequiredTestClass().getSimpleName());
        }
        String resourcePath = annotation.root();
        try {
            var resource = ctx.getRequiredTestClass().getClassLoader().getResource(resourcePath);
            if (resource == null) {
                throw new ParameterResolutionException("Classpath resource not found: " + resourcePath);
            }
            Path path = Path.of(resource.toURI());
            return resourcePath.endsWith("pom.xml") ? path.getParent() : path;
        } catch (URISyntaxException e) {
            throw new ParameterResolutionException("Invalid URI for resource: " + resourcePath, e);
        }
    }

    private static CommandLine buildCli(StringWriter out) {
        CommandLine cmd = new CommandLine(new Main());
        PrintWriter pw = new PrintWriter(out, true);
        cmd.setOut(pw);
        cmd.setErr(pw);
        cmd.setExecutionExceptionHandler((ex, c, pr) -> {
            c.getErr().println("Error: " + ex.getMessage());
            return 1;
        });
        return cmd;
    }
}
