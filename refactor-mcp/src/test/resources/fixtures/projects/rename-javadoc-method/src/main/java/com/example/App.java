package com.example;

/**
 * Uses the {@link Calculator#compute(int, int)} operation.
 *
 * @see Calculator#compute(int, int)
 */
public class App {
    private final Calculator calc = new Calculator();

    public int run(int x, int y) {
        return calc.compute(x, y);
    }
}
