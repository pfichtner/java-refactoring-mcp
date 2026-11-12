package com.example;

public class Beta {
    static {
        // side-effecting class initializer that must not be silently removed
    }

    static int compute(int value) {
        return value * 2;
    }
}
