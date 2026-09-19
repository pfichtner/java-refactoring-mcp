# Introduce static factory rejected: no constructor at offset


### Input: Counter.java:
```java
package com.example;

public class Counter {
    private final int value;
    private final String label;

    public Counter(int value, String label) {
        this.value = value;
        this.label = label;
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
**introduce static factory** `Counter.of`  
line 12, col 16

### Diagnostic:
```
No constructor found at the given offset.
```