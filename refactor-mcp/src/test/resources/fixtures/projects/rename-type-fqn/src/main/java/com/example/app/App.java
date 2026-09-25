package com.example.app;

// No import — uses fully-qualified name throughout
public class App {
    public static void main(String[] args) {
        com.example.service.Rectangle rect = new com.example.service.Rectangle(3, 4);
        System.out.println(rect.area());
    }
}
