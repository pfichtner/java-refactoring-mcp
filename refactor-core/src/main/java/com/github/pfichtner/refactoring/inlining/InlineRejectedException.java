package com.github.pfichtner.refactoring.inlining;

/** Raised when an inline transformation is refused because it cannot be proven safe. */
public class InlineRejectedException extends IllegalArgumentException {

    public InlineRejectedException(String message) {
        super(message);
    }
}
