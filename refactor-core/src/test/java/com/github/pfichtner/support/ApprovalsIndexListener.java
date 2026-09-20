package com.github.pfichtner.support;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.*;

public class ApprovalsIndexListener implements TestExecutionListener {

    private record Entry(String testClass, String testMethod, String title, Path relativePath) {}

    // Top-level categories in display order; unknown classes go to "Other"
    private static final List<Map.Entry<String, List<String>>> CATEGORIES = List.of(
            Map.entry("Rename", List.of(
                    "RenameTest", "HeadlessJdtRenameTest", "RenameEdgeCaseTest", "RenamePackageTest")),
            Map.entry("Extract", List.of(
                    "ExtractMethodTest", "ExtractVariableTest", "ExtractConstantTest",
                    "ExtractInterfaceTest", "ExtractSuperclassTest")),
            Map.entry("Inline", List.of(
                    "InlineMethodTest", "InlineVariableTest", "InlineConstantTest")),
            Map.entry("Introduce", List.of(
                    "IntroduceParamTest", "IntroduceParameterObjectTest", "IntroduceStaticFactoryTest")),
            Map.entry("Remove parameter", List.of("RemoveParamTest")),
            Map.entry("Pull up / Push down", List.of(
                    "PullUpMethodTest", "PullUpFieldTest", "PushDownMethodTest", "PushDownFieldTest")),
            Map.entry("Move class", List.of("MoveClassTest")),
            Map.entry("Convert to record", List.of("ConvertToRecordTest")),
            Map.entry("Build system / project type", List.of(
                    "MavenProjectTest", "GradleProjectTest", "ExplicitProjectTest")),
            Map.entry("CLI", List.of("CliRenameTest")),
            Map.entry("MCP server", List.of("RefactoringServerTest")));

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        try {
            Path root = findProjectRoot();
            List<Entry> entries = collectEntries(root);
            Files.writeString(root.resolve("APPROVALS.md"), generateMarkdown(entries));
        } catch (IOException e) {
            System.err.println("[ApprovalsIndexListener] Failed to update APPROVALS.md: " + e.getMessage());
        }
    }

    private static Path findProjectRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (Files.exists(dir.getParent().resolve("pom.xml"))) {
            dir = dir.getParent();
        }
        return dir;
    }

    private static List<Entry> collectEntries(Path root) throws IOException {
        List<Entry> result = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return dir.getFileName().toString().equals("target")
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                int approvedIdx = name.indexOf(".approved.");
                if (approvedIdx < 0) return FileVisitResult.CONTINUE;
                int firstDot = name.indexOf('.');
                if (firstDot == approvedIdx) return FileVisitResult.CONTINUE; // no test class prefix
                String testClass = name.substring(0, firstDot);
                String testMethod = name.substring(firstDot + 1, approvedIdx);
                String title = name.endsWith(".md") ? extractTitle(file, testMethod) : humanizeMethod(testMethod);
                result.add(new Entry(testClass, testMethod, title, root.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
        result.sort(Comparator.comparing(Entry::testClass).thenComparing(Entry::testMethod));
        return result;
    }

    private static String extractTitle(Path file, String fallback) throws IOException {
        try (var lines = Files.lines(file)) {
            return lines.filter(l -> l.startsWith("# "))
                    .map(l -> l.substring(2).trim())
                    .findFirst()
                    .orElse(humanizeMethod(fallback));
        }
    }

    private static String humanizeMethod(String method) {
        // "inline_void_method_no_params" → "Inline void method no params"
        String s = method.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String generateMarkdown(List<Entry> entries) {
        Map<String, List<Entry>> byClass = entries.stream()
                .collect(Collectors.groupingBy(Entry::testClass, LinkedHashMap::new, Collectors.toList()));

        StringBuilder sb = new StringBuilder();
        sb.append("# Approval Files — Content Overview\n\n");
        sb.append("Index of all golden-master approval files, grouped by refactoring.\n");
        sb.append("Each link opens the approval showing input code, the refactoring applied, and expected output.\n\n");
        sb.append("> Auto-generated by `ApprovalsIndexListener` after each test run — do not edit manually.\n\n");
        sb.append("---\n\n");

        Set<String> placed = new LinkedHashSet<>();

        for (var cat : CATEGORIES) {
            String catName = cat.getKey();
            List<String> classOrder = cat.getValue();
            List<String> present = classOrder.stream().filter(byClass::containsKey).toList();
            if (present.isEmpty()) continue;

            sb.append("## ").append(catName).append("\n\n");
            boolean useSubSections = present.size() > 1;
            for (String cls : present) {
                placed.add(cls);
                if (useSubSections) {
                    sb.append("### ").append(displayName(cls)).append("\n");
                }
                appendTable(sb, byClass.get(cls));
            }
            sb.append("---\n\n");
        }

        // unknown classes land here
        Set<String> unknown = new LinkedHashSet<>(byClass.keySet());
        unknown.removeAll(placed);
        if (!unknown.isEmpty()) {
            sb.append("## Other\n\n");
            for (String cls : unknown) {
                sb.append("### ").append(displayName(cls)).append("\n");
                appendTable(sb, byClass.get(cls));
            }
        }

        return sb.toString();
    }

    private static void appendTable(StringBuilder sb, List<Entry> entries) {
        sb.append("| Test case | Approval |\n");
        sb.append("|-----------|----------|\n");
        for (Entry e : entries) {
            sb.append("| ").append(e.title())
              .append(" | [→](").append(e.relativePath().toString().replace('\\', '/'))
              .append(") |\n");
        }
        sb.append("\n");
    }

    private static String displayName(String className) {
        // "ExtractMethodTest" → "Extract method"
        String s = className.endsWith("Test") ? className.substring(0, className.length() - 4) : className;
        // insert space before uppercase-to-lowercase transitions and before runs-of-uppercase followed by lowercase
        s = s.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2").replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
