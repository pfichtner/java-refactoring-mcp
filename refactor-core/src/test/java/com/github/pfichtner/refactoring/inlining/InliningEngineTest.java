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
    void pure_single_use_argument_is_substituted() throws Exception {
        String source = fixtures.load("inline-aggressive/pure-single-use/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "plusOne(value)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(22);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(22);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: pure single-use argument substituted")
                .javaSection("Input", source)
                .refactoring("inline method", "`plusOne(value)` at " + Fixtures.lineCol(source, offset),
                        "the pure local argument is copied to its single use; no argument temporary")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void pure_multi_use_argument_keeps_temporary() throws Exception {
        String source = fixtures.load("inline-aggressive/pure-multi-use/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "twice(value)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(10);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(10);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: pure argument used twice")
                .javaSection("Input", source)
                .refactoring("inline method", "`twice(value)` at " + Fixtures.lineCol(source, offset),
                        "the pure local argument is read twice, so it stays captured in a temporary")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void pure_argument_assigned_in_callee_keeps_temporary() throws Exception {
        String source = fixtures.load("inline-aggressive/pure-assigned-parameter/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "bump(value)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(102);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(102);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: argument assigned inside callee")
                .javaSection("Input", source)
                .refactoring("inline method", "`bump(value)` at " + Fixtures.lineCol(source, offset),
                        "the callee increments its parameter, so the argument stays a captured temporary")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void pure_argument_used_in_loop_keeps_temporary() throws Exception {
        String source = fixtures.load("inline-aggressive/pure-used-in-loop/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "sumBelow(limit)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(6);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(6);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: argument used in a loop")
                .javaSection("Input", source)
                .refactoring("inline method", "`sumBelow(limit)` at " + Fixtures.lineCol(source, offset),
                        "the single use sits in a loop, so the argument stays a captured temporary")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void pure_argument_captured_by_lambda_keeps_temporary() throws Exception {
        String source = fixtures.load("inline-aggressive/pure-captured-in-lambda/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "plusOneLater(value)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(6);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(6);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: argument captured by a lambda")
                .javaSection("Input", source)
                .refactoring("inline method", "`plusOneLater(value)` at " + Fixtures.lineCol(source, offset),
                        "the single use is captured by a lambda, so the argument stays a captured temporary")
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
    void explicit_receiver_is_captured_once() throws Exception {
        String source = fixtures.load("inline-aggressive/explicit-receiver/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "other.read()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", source, "run"))
                .as("original behaviour")
                .isEqualTo(9);
        assertThat(compileAndInvoke("Foo", result, "run"))
                .as("the receiver is captured once; private members resolve through it")
                .isEqualTo(9);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: explicit receiver captured")
                .javaSection("Input", source)
                .refactoring("inline method", "`other.read()` at " + Fixtures.lineCol(source, offset),
                        "the receiver is bound once and 'this' plus implicit instance members are rewritten")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void explicit_receiver_side_effect_runs_once() throws Exception {
        String source = fixtures.load("inline-aggressive/explicit-receiver-side-effect/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "other.read()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", source, "run"))
                .as("original behaviour evaluates the receiver once")
                .isEqualTo(1);
        assertThat(compileAndInvoke("Foo", result, "run"))
                .as("the side-effecting receiver must still be evaluated once")
                .isEqualTo(1);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: explicit receiver side effect once")
                .javaSection("Input", source)
                .refactoring("inline method", "`other.read()` at " + Fixtures.lineCol(source, offset),
                        "receiver captured once; the body uses it twice")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void explicit_receiver_null_check_is_preserved() throws Exception {
        String source = fixtures.load("inline-aggressive/explicit-receiver-null/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "other.read()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", source, "run"))
                .as("original behaviour")
                .isEqualTo(false);
        assertThat(compileAndInvoke("Foo", result, "run"))
                .as("a null receiver must still throw NullPointerException")
                .isEqualTo(false);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: explicit receiver null check")
                .javaSection("Input", source)
                .refactoring("inline method", "`other.read()` at " + Fixtures.lineCol(source, offset),
                        "the captured receiver gets an explicit null check before the body runs")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void cross_type_explicit_receiver_is_inlined() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/unsafe-explicit-receiver-cross-type/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "other.read()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: cross-type explicit receiver")
                .javaSection("Input", source)
                .refactoring("inline method", "`other.read()` at " + Fixtures.lineCol(source, offset),
                        "receiver captured once; body from non-subtype callee inlined with receiver substitution")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void cross_type_explicit_receiver_with_private_body_is_rejected() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/unsafe-explicit-receiver-cross-type-private/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "other.read()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("private");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: cross-type explicit receiver body has private field")
                .javaSection("Input", source)
                .refactoring("inline method", "`other.read()` at " + Fixtures.lineCol(source, offset),
                        "body references a private field inaccessible from the caller")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void non_static_nested_explicit_receiver_is_rejected() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/unsafe-explicit-receiver-inner/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "other.read()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("non-static nested");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: inner explicit receiver")
                .javaSection("Input", source)
                .refactoring("inline method", "`other.read()` at " + Fixtures.lineCol(source, offset),
                        "rewriting the receiver's enclosing instance is not implemented")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void same_type_generic_explicit_receiver_is_inlined() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/unsafe-explicit-receiver-generic/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "other.read()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: same-type generic explicit receiver")
                .javaSection("Input", source)
                .refactoring("inline method", "`other.read()` at " + Fixtures.lineCol(source, offset),
                        "type variable T is in scope for same-type caller; receiver captured once")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void cross_type_generic_explicit_receiver_without_type_var_is_inlined() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/explicit-receiver-generic-cross-type/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "box.size()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: cross-type generic explicit receiver without type variable")
                .javaSection("Input", source)
                .refactoring("inline method", "`box.size()` at " + Fixtures.lineCol(source, offset),
                        "method body does not reference type variable; receiver captured once")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void cross_type_generic_explicit_receiver_with_typed_return_is_inlined() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/unsafe-explicit-receiver-generic-typevar/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "box.get()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: cross-type generic explicit receiver with typed return")
                .javaSection("Input", source)
                .refactoring("inline method", "`box.get()` at " + Fixtures.lineCol(source, offset),
                        "type variable T substituted with String; receiver captured once")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void cross_type_generic_explicit_receiver_with_wildcard_is_rejected() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/explicit-receiver-generic-cross-type-wildcard/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "box.get()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("wildcard");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: cross-type generic explicit receiver with wildcard type argument")
                .javaSection("Input", source)
                .refactoring("inline method", "`box.get()` at " + Fixtures.lineCol(source, offset),
                        "wildcard type argument cannot be substituted")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void cross_type_generic_explicit_receiver_with_typed_param_is_inlined() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/explicit-receiver-generic-typed-param/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "box.set(value)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: cross-type generic explicit receiver with typed parameter")
                .javaSection("Input", source)
                .refactoring("inline method", "`box.set(value)` at " + Fixtures.lineCol(source, offset),
                        "type variable T substituted with String; receiver captured once")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void cross_type_generic_explicit_receiver_with_parameterized_type_arg_is_inlined() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/explicit-receiver-generic-cross-type-param-arg/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "box.copy()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: cross-type generic explicit receiver with parameterized type argument")
                .javaSection("Input", source)
                .refactoring("inline method", "`box.copy()` at " + Fixtures.lineCol(source, offset),
                        "type variable T substituted with List<String>; body type references structurally replaced")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void cross_type_generic_implicit_receiver_is_inlined() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/cross-type-generic-implicit-receiver/input/StringBox.java");
        int offset = Fixtures.offsetOf(source, "get();");

        String result = InliningEngine.inlineAt(source, "StringBox.java", offset);
        assertCompiles("StringBox", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: cross-type generic implicit receiver")
                .javaSection("Input", source)
                .refactoring("inline method", "`get()` at " + Fixtures.lineCol(source, offset),
                        "inherited method from generic supertype; type variable T substituted with String")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void cross_type_generic_implicit_receiver_raw_is_rejected() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/cross-type-generic-implicit-receiver-raw/input/RawBox.java");
        int offset = Fixtures.offsetOf(source, "get();");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "RawBox.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("raw generic type");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: cross-type generic implicit receiver with raw type")
                .javaSection("Input", source)
                .refactoring("inline method", "`get()` at " + Fixtures.lineCol(source, offset),
                        "raw generic type: type substitution not possible")
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
    void nested_expression_in_return_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/unsupported-position/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(3);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(3);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: nested expression in return")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "pure literal `1` before the call; hoisting does not reorder any observable effect")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void getter_body_substituted_at_right_of_impure_infix() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-nested-side-effect/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(2);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(2);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: pure getter substituted at right of impure infix")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "single-return pure body; substituted directly without hoisting")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void non_getter_body_nested_in_impure_infix_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/getter-impure-body-nested/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("side effects");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: non-getter body in impure infix")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "multi-statement body is not a getter; hoisting would reorder sideEffect()")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void getter_body_substituted_in_argument_position() throws Exception {
        String source = fixtures.load("inline-aggressive/getter-nested-argument/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "getValue()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(21);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(21);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: getter substituted in argument position")
                .javaSection("Input", source)
                .refactoring("inline method", "`getValue()` at " + Fixtures.lineCol(source, offset),
                        "single-return field getter; substituted in-place after impure left operand")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void try_finally_runs_before_return() throws Exception {
        String source = fixtures.load("inline-aggressive/try-finally/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "choose()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", source, "run"))
                .as("original behaviour")
                .isEqualTo(12);
        assertThat(compileAndInvoke("Foo", result, "run"))
                .as("finally must still run before the inlined return completes")
                .isEqualTo(12);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: finally runs before return")
                .javaSection("Input", source)
                .refactoring("inline method", "`choose()` at " + Fixtures.lineCol(source, offset),
                        "return becomes an assignment plus a labeled break; the finally block still runs")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void try_finally_return_overrides_earlier_return() throws Exception {
        String source = fixtures.load("inline-aggressive/try-finally-return/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "choose()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", source, "run"))
                .as("original behaviour")
                .isEqualTo(2);
        assertThat(compileAndInvoke("Foo", result, "run"))
                .as("a return in finally still overrides the try return")
                .isEqualTo(2);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: return in finally overrides")
                .javaSection("Input", source)
                .refactoring("inline method", "`choose()` at " + Fixtures.lineCol(source, offset),
                        "both returns are rewritten; the finally assignment runs last")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void generated_labels_and_names_do_not_collide() throws Exception {
        String source = fixtures.load("inline-aggressive/labels/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "twice(n)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", result, "run"))
                .as("caller and callee labels/locals named like generated names")
                .isEqualTo(10);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: generated labels avoid existing names")
                .javaSection("Input", source)
                .refactoring("inline method", "`twice(n)` at " + Fixtures.lineCol(source, offset),
                        "existing __inline_* labels and locals force suffixed generated names")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void captured_locals_are_renamed_consistently() throws Exception {
        String source = fixtures.load("inline-aggressive/capture/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "pick(5)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", result, "run"))
                .as("lambda, anonymous class, and local class keep the captured values")
                .isEqualTo(111);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: captured locals renamed consistently")
                .javaSection("Input", source)
                .refactoring("inline method", "`pick(5)` at " + Fixtures.lineCol(source, offset),
                        "captured parameter and colliding local are renamed inside lambda, anonymous and local class")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void pattern_for_and_catch_variables_are_renamed() throws Exception {
        String source = fixtures.load("inline-aggressive/pattern-for-catch/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "total(\"ab,cd\")");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", result, "run"))
                .as("pattern, enhanced-for, and catch variables keep their scopes")
                .isEqualTo(20);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: pattern, for, and catch variables")
                .javaSection("Input", source)
                .refactoring("inline method", "`total(\"ab,cd\")` at " + Fixtures.lineCol(source, offset),
                        "colliding pattern, enhanced-for, and catch variables are renamed by binding")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void overload_and_this_receiver_are_resolved_by_binding() throws Exception {
        String source = fixtures.load("inline-aggressive/overloads/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "this.pick(3)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", result, "run"))
                .as("the int overload is inlined, not the String overload")
                .isEqualTo(13);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: overload and explicit this receiver")
                .javaSection("Input", source)
                .refactoring("inline method", "`this.pick(3)` at " + Fixtures.lineCol(source, offset),
                        "binding key selects the int overload; explicit this receiver is accepted")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void simple_name_assignment_is_accepted() throws Exception {
        String source = fixtures.load("inline-aggressive/assignment-simple/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(7);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: simple-name assignment")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "assignment right-hand side with a side-effect-free simple-name left-hand side")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void field_assignment_is_accepted() throws Exception {
        String source = fixtures.load("inline-aggressive/assignment-field/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(7);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: field assignment")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "assignment right-hand side with a side-effect-free `this.field` left-hand side")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void type_qualified_static_call_is_accepted() throws Exception {
        String source = fixtures.load("inline-aggressive/static-qualified/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "Foo.compute(2)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);

        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(6);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: type-qualified static call")
                .javaSection("Input", source)
                .refactoring("inline method", "`Foo.compute(2)` at " + Fixtures.lineCol(source, offset),
                        "static method called through its type; same-type class initialization is unchanged")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void super_call_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-super/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "read()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("super method calls");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: super call")
                .javaSection("Input", source)
                .refactoring("inline method", "`super.read()` at " + Fixtures.lineCol(source, offset),
                        "super calls are not method invocations and have different dispatch semantics")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void constructor_call_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-constructor-call/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "new Foo()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("constructor calls");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: constructor call")
                .javaSection("Input", source)
                .refactoring("inline method", "`new Foo()` at " + Fixtures.lineCol(source, offset),
                        "constructors are not method calls and cannot be inlined")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void method_reference_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-method-reference/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "this::value");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("method references");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: method reference")
                .javaSection("Input", source)
                .refactoring("inline method", "`this::value` at " + Fixtures.lineCol(source, offset),
                        "a method reference is not an invocation and cannot be inlined")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void assignment_with_side_effecting_lhs_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-assignment-lhs/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("side effects");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: side-effecting assignment LHS")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "array index evaluation would be reordered relative to the inlined body")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void getter_body_substituted_in_assignment_rhs_despite_side_effecting_lhs() throws Exception {
        String source = fixtures.load("inline-aggressive/getter-assignment-rhs/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: getter substituted in assignment RHS despite side-effecting LHS")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "single-return pure getter substituted in-place; LHS evaluation order preserved")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void delegation_substituted_at_nested_position_via_inlineAt() throws Exception {
        String source = fixtures.load("inline-aggressive/delegation-nested/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "doubled(n)");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: delegation substituted at nested position (inlineAt)")
                .javaSection("Input", source)
                .refactoring("inline method", "`doubled(n)` at " + Fixtures.lineCol(source, offset),
                        "delegation body `return twice(n)` substituted in-place; hoisting is impossible"
                        + " because the left operand `nonInlineable(n)` is a method call")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void delegation_chain_fully_resolved_via_inlineAll() throws Exception {
        String source = fixtures.load("inline-aggressive/delegation-nested/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");
        assertCompiles("Foo", result.source());

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: delegation chain fully resolved (inlineAll)")
                .javaSection("Input", source)
                .refactoring("inline all", "`doubled` → delegation substitution; `twice` → getter substitution",
                        "two passes collapse the delegation chain: delegation replaces `doubled(n)` with"
                        + " `twice(n)`, then getter inlining replaces `twice(n)` with `n * 2`")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void direct_recursion_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/recursion-direct/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "countdown(value - 1)");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("recursive call");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: direct recursion")
                .javaSection("Input", source)
                .refactoring("inline method", "`countdown(value - 1)` at " + Fixtures.lineCol(source, offset),
                        "inlining a call into its own declaration cannot terminate")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void non_completing_void_body_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/non-completing/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "loop()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("cannot complete normally");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: non-completing void body")
                .javaSection("Input", source)
                .refactoring("inline method", "`loop()` at " + Fixtures.lineCol(source, offset),
                        "pasting an infinite loop before the return makes following code unreachable")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void max_transformations_stops_chain() throws Exception {
        String source = fixtures.load("inline-aggressive/chain/input/Chain.java");

        InliningResult result = InliningEngine.inlineAll(
                source, "Chain.java", new InlineConfig(1, 100_000));

        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.fixedPoint()).isFalse();
        assertCompiles("Chain", result.source());

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: maxTransformations budget stops the chain")
                .javaSection("Input", source)
                .refactoring("inline all", "`first`, `second`, `third`",
                        "one transformation applied, then the maxTransformations budget stops the run")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void max_code_growth_stops_run_without_reporting_fixed_point() throws Exception {
        String source = fixtures.load("inline-aggressive/private-simple/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(
                source, "Foo.java", new InlineConfig(100, 0));

        assertThat(result.transformationCount()).isZero();
        assertThat(result.fixedPoint()).isFalse();
        assertThat(result.source()).isEqualTo(source);
        assertThat(result.rejected())
                .anySatisfy(rejection -> assertThat(rejection.reason())
                        .contains("code growth limit exceeded"));
    }

    @Test
    void mutual_recursion_stops_at_budget() throws Exception {
        String source = fixtures.load("inline-aggressive/mutual-recursion/input/Mutual.java");

        InliningResult result = InliningEngine.inlineAll(
                source, "Mutual.java", new InlineConfig(4, 1_000_000));

        assertThat(result.transformationCount()).isEqualTo(4);
        assertThat(result.fixedPoint()).isFalse();
        assertThat(result.rejected())
                .anySatisfy(rejection -> assertThat(rejection.reason()).contains("mutual recursion"));
        assertCompiles("Mutual", result.source());

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: mutual recursion stopped by budget")
                .javaSection("Input", source)
                .refactoring("inline all", "`first`, `second`",
                        "first↔second cross-calls are rejected as mutual recursion; start is unrolled until budget")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void unused_declarations_are_removed_after_inlining() throws Exception {
        String source = fixtures.load("inline-aggressive/unused-after-inlining/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(
                source, "Foo.java", new InlineConfig(100, 100_000, true));

        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.fixedPoint()).isTrue();
        assertThat(result.removed()).hasSize(1);
        assertThat(result.removed().get(0).methodName()).isEqualTo("helper");
        assertThat(result.source()).doesNotContain("private int helper");
        assertCompiles("Foo", result.source());
        assertThat(compileAndInvoke("Foo", result.source(), "run")).isEqualTo(42);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: unused declaration removed after inlining")
                .javaSection("Input", source)
                .refactoring("inline all with removal", "`helper()`",
                        "helper() is inlined into run() and removed because no call sites remain")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void constant_int_field_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/constant-int/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");

        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.fixedPoint()).isTrue();
        assertCompiles("Foo", result.source());
        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(100);
        assertThat(compileAndInvoke("Foo", result.source(), "run")).isEqualTo(100);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: static final int constant")
                .javaSection("Input", source)
                .refactoring("inline all", "`LIMIT`",
                        "static final int constant replaced by its literal value")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void constant_string_field_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/constant-string/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");

        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.fixedPoint()).isTrue();
        assertCompiles("Foo", result.source());
        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo("hello world");
        assertThat(compileAndInvoke("Foo", result.source(), "run")).isEqualTo("hello world");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: static final String constant")
                .javaSection("Input", source)
                .refactoring("inline all", "`PREFIX`",
                        "static final String constant replaced by its literal value")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void constant_boolean_field_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/constant-boolean/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");

        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.fixedPoint()).isTrue();
        assertCompiles("Foo", result.source());
        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(true);
        assertThat(compileAndInvoke("Foo", result.source(), "run")).isEqualTo(true);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: static final boolean constant")
                .javaSection("Input", source)
                .refactoring("inline all", "`ENABLED`",
                        "static final boolean constant replaced by its literal value")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void constant_inlined_after_method_inline() throws Exception {
        String source = fixtures.load("inline-aggressive/constant-after-method-inline/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");

        // 1 method + 2 constants (LIMIT appears in both compute()'s body and the inlined body in run())
        assertThat(result.transformationCount()).isEqualTo(3);
        assertThat(result.fixedPoint()).isTrue();
        assertCompiles("Foo", result.source());
        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(100);
        assertThat(compileAndInvoke("Foo", result.source(), "run")).isEqualTo(100);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: constant inlined after method inline")
                .javaSection("Input", source)
                .refactoring("inline all", "`compute`, then `LIMIT` twice",
                        "method inlined first; constant then replaced in run() and compute()")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void final_field_int_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/final-field-int/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");

        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.fixedPoint()).isTrue();
        assertCompiles("Foo", result.source());
        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(42);
        assertThat(compileAndInvoke("Foo", result.source(), "run")).isEqualTo(42);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: private final int field")
                .javaSection("Input", source)
                .refactoring("inline all", "`VALUE`",
                        "private final int field replaced by its literal initializer")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void final_field_string_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/final-field-string/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");

        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.fixedPoint()).isTrue();
        assertCompiles("Foo", result.source());
        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo("hello world");
        assertThat(compileAndInvoke("Foo", result.source(), "run")).isEqualTo("hello world");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: private final String field")
                .javaSection("Input", source)
                .refactoring("inline all", "`PREFIX`",
                        "private final String field replaced by its literal initializer")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void final_field_accessed_via_this_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/final-field-this-access/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");

        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.fixedPoint()).isTrue();
        assertCompiles("Foo", result.source());
        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(42);
        assertThat(compileAndInvoke("Foo", result.source(), "run")).isEqualTo(42);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: private final field via this reference")
                .javaSection("Input", source)
                .refactoring("inline all", "`this.VALUE`",
                        "explicit this.field access replaced by the literal initializer")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void final_field_with_arithmetic_initializer_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/final-field-arithmetic/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");

        assertThat(result.transformationCount()).isEqualTo(1);
        assertThat(result.fixedPoint()).isTrue();
        assertCompiles("Foo", result.source());
        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(70);
        assertThat(compileAndInvoke("Foo", result.source(), "run")).isEqualTo(70);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: private final field with arithmetic initializer")
                .javaSection("Input", source)
                .refactoring("inline all", "`BASE`",
                        "compound initializer wrapped in parens to preserve operator precedence")
                .javaSection("Output", result.source())
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Phase 5d — Delegating constructor inlining
    // -------------------------------------------------------------------------

    @Test
    void delegating_constructor_no_args_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/delegating-constructor/input/Counter.java");
        int offset = Fixtures.offsetOf(source, "this(0)");

        String result = InliningEngine.inlineAt(source, "Counter.java", offset);
        assertCompiles("Counter", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: delegating constructor")
                .javaSection("Input", source)
                .refactoring("inline constructor", "`this(0)` at " + Fixtures.lineCol(source, offset),
                        "this() call replaced by target constructor body")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void delegating_constructor_with_params_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/delegating-constructor-params/input/Range.java");
        int offset = Fixtures.offsetOf(source, "this(start, start + 10)");

        String result = InliningEngine.inlineAt(source, "Range.java", offset);
        assertCompiles("Range", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: delegating constructor with params")
                .javaSection("Input", source)
                .refactoring("inline constructor",
                        "`this(start, start + 10)` at " + Fixtures.lineCol(source, offset),
                        "parameters bound to temporaries; this() call replaced by target body")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void delegating_constructor_with_early_return_is_inlined() throws Exception {
        String source = fixtures.load(
                "inline-aggressive/delegating-constructor-early-return/input/Validated.java");
        int offset = Fixtures.offsetOf(source, "this(0)");

        String result = InliningEngine.inlineAt(source, "Validated.java", offset);
        assertCompiles("Validated", result);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: delegating constructor with early return")
                .javaSection("Input", source)
                .refactoring("inline constructor", "`this(0)` at " + Fixtures.lineCol(source, offset),
                        "early return rewritten to labeled break; this() call replaced by target body")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void delegating_constructor_inlined_all_passes() throws Exception {
        String source = fixtures.load("inline-aggressive/delegating-constructor/input/Counter.java");

        InliningResult result = InliningEngine.inlineAll(source, "Counter.java");
        assertCompiles("Counter", result.source());

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline all: delegating constructor")
                .javaSection("Input", source)
                .refactoring("inline all", "`Counter()` delegating constructor",
                        "this() call replaced by target constructor body; fixed point reached")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void delegating_constructor_with_explicit_super_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-delegating-super-call/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "this(0)");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("super()");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: delegating constructor with super()")
                .javaSection("Input", source)
                .refactoring("inline constructor", "`this(0)` at " + Fixtures.lineCol(source, offset),
                        "rejected: target has explicit super() call")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void delegating_constructor_with_chained_this_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-delegating-chain/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "this(42)");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("chain");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: chained delegating constructor")
                .javaSection("Input", source)
                .refactoring("inline constructor", "`this(42)` at " + Fixtures.lineCol(source, offset),
                        "rejected: target also delegates via this()")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Phase 5f — Anonymous-class-to-lambda flattening
    // -------------------------------------------------------------------------

    @Test
    void anonymous_runnable_is_converted_to_lambda() throws Exception {
        String source = fixtures.load("inline-aggressive/anon-runnable/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");
        assertCompiles("Foo", result.source());

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: anonymous Runnable flattened to lambda")
                .javaSection("Input", source)
                .refactoring("flatten anonymous class", "anonymous Runnable",
                        "single-statement void body; expression-form lambda")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void anonymous_comparator_is_converted_to_lambda() throws Exception {
        String source = fixtures.load("inline-aggressive/anon-comparator/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");
        assertCompiles("Foo", result.source());

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: anonymous Comparator flattened to lambda")
                .javaSection("Input", source)
                .refactoring("flatten anonymous class", "anonymous Comparator with two parameters",
                        "single-return body; expression-form lambda with inferred parameter types")
                .javaSection("Output", result.source())
                .build()
        );
    }

    @Test
    void anonymous_class_using_this_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/anon-with-this/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");

        assertThat(result.applied()).isEmpty();
        assertThat(result.rejected())
                .anyMatch(r -> r.reason().contains("'this'"));

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: anonymous class uses 'this'")
                .javaSection("Input", source)
                .refactoring("flatten anonymous class", "anonymous Runnable",
                        "rejected: 'this' in lambda would refer to the enclosing type, not the anonymous class")
                .diagnostic(result.rejected().get(0).reason())
                .build()
        );
    }

    @Test
    void anonymous_class_with_fields_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/anon-with-fields/input/Foo.java");

        InliningResult result = InliningEngine.inlineAll(source, "Foo.java");

        assertThat(result.applied()).isEmpty();
        assertThat(result.rejected())
                .anyMatch(r -> r.reason().contains("body declarations"));

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: anonymous class has fields")
                .javaSection("Input", source)
                .refactoring("flatten anonymous class", "anonymous Runnable",
                        "rejected: anonymous class has field declarations beyond the SAM override")
                .diagnostic(result.rejected().get(0).reason())
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Phase 11 — Additional safe call positions
    // -------------------------------------------------------------------------

    @Test
    void call_in_if_condition_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/if-condition/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(1);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(1);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: call in if-condition")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "if-condition position; body hoisted before if-statement, condition replaced with result")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void call_as_ternary_condition_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/ternary-condition/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(1);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(1);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: call as ternary condition")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "ternary condition always evaluated; body hoisted before enclosing statement")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void call_in_ternary_then_branch_is_rejected() throws Exception {
        String source = fixtures.load("inline-aggressive/unsafe-ternary-branch/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        InlineRejectedException exception = assertThatExceptionOfType(InlineRejectedException.class)
                .isThrownBy(() -> InliningEngine.inlineAt(source, "Foo.java", offset))
                .actual();
        assertThat(exception.getMessage()).contains("conditional expression branch");

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline — rejected: call in ternary then-branch")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "then-branch may not be evaluated; hoisting before statement would always evaluate it")
                .diagnostic(exception.getMessage())
                .build()
        );
    }

    @Test
    void call_in_constructor_argument_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/constructor-argument/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "capacity()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(1);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(1);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: call in constructor argument")
                .javaSection("Input", source)
                .refactoring("inline method", "`capacity()` at " + Fixtures.lineCol(source, offset),
                        "constructor argument; no preceding side-effecting argument; body hoisted before enclosing statement")
                .javaSection("Output", result)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Phase 12 — Additional safe call positions (throw, switch)
    // -------------------------------------------------------------------------

    @Test
    void call_in_throw_expression_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/throw-expression/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(5);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(5);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: call in throw expression")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "throw expression; body hoisted before throw statement, expression replaced with result")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void call_as_switch_selector_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/switch-selector/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(10);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(10);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: call as switch selector")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "switch selector; body hoisted before switch statement, selector replaced with result")
                .javaSection("Output", result)
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Phase 13 — Additional safe call positions (array dimension, enhanced-for)
    // -------------------------------------------------------------------------

    @Test
    void call_as_array_dimension_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/array-dimension/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(3);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(3);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: call as array dimension")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "array dimension; body hoisted before enclosing statement, dimension replaced with result")
                .javaSection("Output", result)
                .build()
        );
    }

    @Test
    void call_as_enhanced_for_iterable_is_inlined() throws Exception {
        String source = fixtures.load("inline-aggressive/enhanced-for-iterable/input/Foo.java");
        int offset = Fixtures.offsetOf(source, "compute()");

        String result = InliningEngine.inlineAt(source, "Foo.java", offset);
        assertCompiles("Foo", result);

        assertThat(compileAndInvoke("Foo", source, "run")).isEqualTo(6);
        assertThat(compileAndInvoke("Foo", result, "run")).isEqualTo(6);

        Approvals.verify(
            RefactoringStoryBoard.titled("Aggressive inline: call as enhanced-for iterable")
                .javaSection("Input", source)
                .refactoring("inline method", "`compute()` at " + Fixtures.lineCol(source, offset),
                        "enhanced-for iterable; body hoisted before for statement, iterable replaced with result")
                .javaSection("Output", result)
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
