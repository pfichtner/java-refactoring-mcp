package com.example;

public class Dog extends Animal {
    protected String name;
    protected String breed;

    public String describe() {
        return name + " (" + breed + ")";
    }
}
