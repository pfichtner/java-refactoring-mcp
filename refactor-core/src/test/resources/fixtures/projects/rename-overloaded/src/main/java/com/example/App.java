package com.example;

public class App {
    public void run() {
        Overloaded o = new Overloaded();
        int r2 = o.compute(1, 2);
        int r3 = o.compute(1, 2, 3);
    }
}
