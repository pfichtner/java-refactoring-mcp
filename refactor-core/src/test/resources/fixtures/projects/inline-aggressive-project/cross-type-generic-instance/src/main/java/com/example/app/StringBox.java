package com.example.app;

import com.example.base.Box;

public class StringBox extends Box<String> {
    public StringBox() {
        this.item = "Hello";
    }

    public String run() {
        return get();
    }
}
