package com.example;

import java.util.function.IntBinaryOperator;

public class App {
    public static void main(String[] args) {
        Computation calc = new Computation();
        IntBinaryOperator op = calc::add;
        int result = calc.add(1, 2);
        System.out.println(result);
    }
}
