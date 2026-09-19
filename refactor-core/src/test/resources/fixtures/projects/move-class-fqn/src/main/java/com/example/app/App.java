package com.example.app;

// No import — uses fully-qualified name throughout
public class App {
    public static void main(String[] args) {
        com.example.service.Calculator calc = new com.example.service.Calculator();
        System.out.println(calc.add(1, 2));
    }
}
