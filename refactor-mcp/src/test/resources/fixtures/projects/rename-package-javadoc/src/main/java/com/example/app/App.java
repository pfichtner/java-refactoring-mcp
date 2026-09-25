package com.example.app;

/**
 * Application entry point.
 * Delegates arithmetic to {@link com.example.service.Calculator}.
 *
 * @see com.example.service.Calculator
 */
public class App {
    public static void main(String[] args) {
        com.example.service.Calculator calc = new com.example.service.Calculator();
        System.out.println(calc.add(1, 2));
    }
}
