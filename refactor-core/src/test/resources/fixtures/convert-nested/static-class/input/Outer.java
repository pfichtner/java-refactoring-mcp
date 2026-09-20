package com.example;

public class Outer {

    private String name;

    public Outer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private static class Helper {

        static int doubleIt(int x) {
            return x * 2;
        }
    }
}
