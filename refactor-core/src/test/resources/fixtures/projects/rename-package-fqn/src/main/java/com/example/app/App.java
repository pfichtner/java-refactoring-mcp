package com.example.app;

import java.util.function.Supplier;

// No import for Calculator — uses fully-qualified names throughout
public class App {

    // FQN in field type and initializer
    private com.example.service.Calculator calc = new com.example.service.Calculator();

    public static void main(String[] args) {
        // FQN in local variable declaration
        com.example.service.Calculator c = new com.example.service.Calculator();

        // FQN inside a lambda
        Supplier<com.example.service.Calculator> factory =
                () -> new com.example.service.Calculator();

        System.out.println(c.add(1, 2));
    }
}
