package com.example;

public class App {
    public static void main(String[] args) {
        Counter c1 = new Counter(0, "start");
        Counter c2 = new Counter(42, "answer");
        System.out.println(c1.label() + ": " + c1.value());
        System.out.println(c2.label() + ": " + c2.value());
    }
}
