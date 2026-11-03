package com.github.pfichtner.refactoring.inlining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.approvaltests.Approvals;
import org.junit.jupiter.api.Test;

import com.github.pfichtner.refactoring.support.Fixtures;
import com.github.pfichtner.refactoring.support.RefactoringStoryBoard;

/**
 * Approval and semantic tests for the isolated aggressive-inlining spike.
 * The approved markdown files are the specification for the rewritten source.
 */
class InliningEngineTest {

    private final Fixtures fixtures = new Fixtures(getClass());

    @Test
    void inline_simple_private_method() throws Exception {
        String source = fixtures.load("inline-aggressive/private-simple/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "twice(21)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: simple private method")
                .javaSection("Input", source)
                .refactoring("inline method", "`twice(21)` at " + Fixtures.lineCol(source, offset),
                        "argument bound to a temporary; return value hoisted into a result temporary")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_method_with_local_variables() throws Exception {
        String source = fixtures.load("inline-aggressive/locals/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "adjust(input)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: method with local variables")
                .javaSection("Input", source)
                .refactoring("inline method", "`adjust(input)` at " + Fixtures.lineCol(source, offset),
                        "body locals are copied and kept when they do not collide with caller names")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_method_with_multiple_returns() throws Exception {
        String source = fixtures.load("inline-aggressive/multiple-returns/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "sign(n)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: multiple return statements")
                .javaSection("Input", source)
                .refactoring("inline method", "`sign(n)` at " + Fixtures.lineCol(source, offset),
                        "each return becomes an assignment plus a break out of the labeled block")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void argument_side_effects_are_evaluated_once() throws Exception {
        String source = fixtures.load("inline-aggressive/side-effects/input/SideEffect.java");
        int offset = Fixtures.offsetOf(source, "twice(increment())");

        String result = InliningEngine.inlineAt(source, "SideEffect.java", offset);

        assertThat(compileAndInvoke("SideEffect", source, "run"))
                .as("original behaviour")
                .isEqualTo(2);
        assertThat(compileAndInvoke("SideEffect", result, "run"))
                .as("inlined behaviour: increment() must run once, not once per parameter use")
                .isEqualTo(2);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: argument side effects evaluated once")
                .javaSection("Input", source)
                .refactoring("inline method", "`twice(increment())` at " + Fixtures.lineCol(source, offset),
                        "the side-effecting argument is bound once; the parameter is used twice")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void inline_chain_reaches_fixed_point() throws Exception {
        String source = fixtures.load("inline-aggressive/chain/input/Chain.java");

        InliningResult result = InliningEngine.inlineAll(
                source, "Chain.java", new InlineConfig(20, 100_000));

        assertThat(result.transformationCount()).isEqualTo(3);
        assertThat(result.fixedPoint()).isTrue();
        assertThat(compileAndInvoke("Chain", result.source(), "start")).isEqualTo(1);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: iterative chain until fixed point")
                .javaSection("Input", source)
                .refactoring("inline all", "`first`, `second`, `third`",
                        "three passes; each pass re-parses the rewritten source")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void unsafe_synchronized_method_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-synchronized/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "guarded(2)");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("synchronized");

        InliningResult iterative = InliningEngine.inlineAll(source, "Foo.java");
        assertThat(iterative.transformationCount()).isZero();
        assertThat(iterative.rejected())
                .anySatisfy(rejection -> assertThat(rejection.reason()).contains("synchronized"));

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: synchronized method")
                .javaSection("Input", source)
                .refactoring("inline method", "`guarded(2)` at " + Fixtures.lineCol(source, offset),
                        "inlining would remove the method-level monitor")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void unsafe_explicit_receiver_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-receiver/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "other.read()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("explicit receiver");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: explicit receiver")
                .javaSection("Input", source)
                .refactoring("inline method", "`other.read()` at " + Fixtures.lineCol(source, offset),
                        "'this' inside the body refers to the receiver, not the caller")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void checked_exception_method_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-checked-exception/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "read()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("checked exception");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: checked exception")
                .javaSection("Input", source)
                .refactoring("inline method", "`read()` at " + Fixtures.lineCol(source, offset),
                        "inlining would move checked-exception handling to the call site")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void nested_expression_call_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsupported-position/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("whole expression");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: nested expression position")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "hoisting before the statement would reorder evaluation relative to `1 +`")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Compilation helpers
    // -------------------------------------------------------------------------

    private static void assertCompiles(String className, String source) throws Exception {
        compile(className, source);
    }

    private static Object compileAndInvoke(
            String className, String source, String methodName) throws Exception {
        Path classes = compile(className, source);
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL()}, ClassLoader.getSystemClassLoader())) {
            Class<?> type = Class.forName(className, true, loader);
            Object instance = type.getDeclaredConstructor().newInstance();
            return type.getMethod(methodName).invoke(instance);
        }
    }

    private static Path compile(String className, String source) throws Exception {
        Path directory = Files.createTempDirectory("inline-spike");
        Path file = directory.resolve(className + ".java");
        Files.writeString(file, source);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler must be available").isNotNull();
        int exitCode = compiler.run(null, null, null,
                "-d", directory.toString(), file.toString());
        assertThat(exitCode)
                .as("transformed source must compile:%n%s", source)
                .isZero();
        return directory;
    }
}
