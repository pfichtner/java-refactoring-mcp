package com.example;

public class App {
    public static void main(String[] args) {
        Converter c = new Converter();
        System.out.println(c.convert(42, "val:"));
        System.out.println(c.convert(7, "id:"));
    }
}
