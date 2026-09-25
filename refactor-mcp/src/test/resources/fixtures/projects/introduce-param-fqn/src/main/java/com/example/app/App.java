package com.example.app;

// No import — Calculator referenced by FQN in method body
public class App {
    public int compute() {
        return new com.example.service.Calculator().add(1, 2);
    }

    public void run() {
        compute();
    }
}
