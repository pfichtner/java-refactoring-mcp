package com.example;

import java.util.function.Consumer;

public class App {
    public static void main(String[] args) {
        Greeter g = new Greeter();
        Runnable r = g::greet;
        Consumer<Greeter> c = Greeter::greet;
    }
}
