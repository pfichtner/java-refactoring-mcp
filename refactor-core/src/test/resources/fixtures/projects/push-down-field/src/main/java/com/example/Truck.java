package com.example;

public class Truck extends Vehicle {
    public boolean canHighway() {
        return maxSpeed >= 80;
    }
}
