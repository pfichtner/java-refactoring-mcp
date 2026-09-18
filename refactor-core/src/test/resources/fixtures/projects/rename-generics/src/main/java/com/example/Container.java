package com.example;

public class Container<T> {
    private T value;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public static <T> Container<T> of(T value) {
        Container<T> c = new Container<>();
        c.setValue(value);
        return c;
    }
}
