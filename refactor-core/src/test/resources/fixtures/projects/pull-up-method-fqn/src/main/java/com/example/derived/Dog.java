package com.example.derived;

// Fully-qualified extends clause — no import for Animal
public class Dog extends com.example.base.Animal {
    public void speak() {
        System.out.println("Woof!");
    }
}
