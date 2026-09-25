package com.example;

public class App {
    public static void main(String[] args) {
        Computation calc = new Computation();
        int result = calc.add(1, 2, 99);
        System.out.println(result);
    }
}
