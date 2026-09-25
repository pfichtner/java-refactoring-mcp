package com.example;

public class Container {

    private Object value;

    public interface Transformer {
        Object transform(Object input);
    }

    public Object apply(Transformer t) {
        return t.transform(value);
    }
}
