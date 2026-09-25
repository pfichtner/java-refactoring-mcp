package com.example;

public class App {
    public static void main(String[] args) {
        Point p = new Point(3, 4);
        System.out.println(p.getX());
        System.out.println(p.getY());
        Point origin = new Point(0, 0);
        System.out.println(p.distanceTo(origin));
    }
}
