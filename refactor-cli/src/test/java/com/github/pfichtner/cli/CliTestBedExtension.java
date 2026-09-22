package com.github.pfichtner.cli;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

class CliTestBedExtension implements ParameterResolver {

    private static final Namespace NS = Namespace.create(CliTestBedExtension.class);

    @Override
    public boolean supportsParameter(ParameterContext param, ExtensionContext ctx) {
        return param.getParameter().getType() == CliTestBed.class;
    }

    @Override
    public Object resolveParameter(ParameterContext param, ExtensionContext ctx) {
        return ctx.getStore(NS).getOrComputeIfAbsent("bed", k -> createBed(ctx), CliTestBed.class);
    }

    private CliTestBed createBed(ExtensionContext ctx) {
        CliFixture annotation = ctx.getRequiredTestClass().getAnnotation(CliFixture.class);
        if (annotation == null) {
            throw new ParameterResolutionException(
                    "@CliFixture not found on " + ctx.getRequiredTestClass().getSimpleName());
        }
        String resourcePath = annotation.root();
        try {
            URL resource = ctx.getRequiredTestClass().getClassLoader().getResource(resourcePath);
            if (resource == null) {
                throw new ParameterResolutionException("Classpath resource not found: " + resourcePath);
            }
            Path source = Path.of(resource.toURI());
            Path tmpDir = Files.createTempDirectory("cli-testbed-");
            registerCleanup(ctx, tmpDir);

            Path root;
            if (resourcePath.endsWith("pom.xml")) {
                copyTree(source.getParent(), tmpDir);
                root = tmpDir;
            } else {
                Path dest = tmpDir.resolve(source.getFileName());
                Files.copy(source, dest);
                root = dest;
            }
            return new CliTestBed(root);
        } catch (ParameterResolutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ParameterResolutionException("Failed to create test bed for " + resourcePath, e);
        }
    }

    private static void registerCleanup(ExtensionContext ctx, Path tmpDir) {
        ctx.getStore(NS).put("tmpDir-cleanup",
                (Store.CloseableResource) () -> deleteRecursively(tmpDir));
    }

    private static void copyTree(Path src, Path dst) throws Exception {
        try (var stream = Files.walk(src)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                Path target = dst.resolve(src.relativize(p));
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target);
            }
        }
    }

    private static void deleteRecursively(Path dir) throws Exception {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                  .map(Path::toFile)
                  .forEach(java.io.File::delete);
        }
    }
}
