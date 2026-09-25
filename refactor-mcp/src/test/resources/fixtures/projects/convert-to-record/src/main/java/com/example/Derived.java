package com.example;

public class Derived extends Base {
    private final int value;

    public Derived(int value) {
        this.value = value;
    }

    public int getValue() { return value; }
}
