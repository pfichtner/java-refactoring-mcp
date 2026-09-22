# Introduce static factory rejected: factory method already exists


### Input: Counter.java (already has 'of'):
```java
package com.example;

public class Counter {
    private final int value;
    private final String label;

    public Counter(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public static Counter of(int value, String label) {
        return new Counter(value, label);
    }

    public int value() {
        return value;
    }

    public String label() {
        return label;
    }
}
```

### Refactoring:
**introduce static factory** duplicate `Counter.of`  
line 7, col 12

### Diagnostic:
```
Class 'Counter' already has a method 'of' with 2 parameter(s).
```