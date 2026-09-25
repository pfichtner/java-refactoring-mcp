package com.example;

/**
 * Application entry point.
 * Uses {@link Greeter} to generate greetings.
 *
 * @see Greeter
 */
public class App {
    private Greeter greeter = new Greeter();

    public void run() {
        System.out.println(greeter.greet("World"));
    }
}
