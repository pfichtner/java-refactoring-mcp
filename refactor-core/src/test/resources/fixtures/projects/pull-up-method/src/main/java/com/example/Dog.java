package com.example;

public class Dog extends Animal {
    @Override
    public String name() {
        return "Dog";
    }

    public void speak() {
        System.out.println("Woof!");
    }
}
