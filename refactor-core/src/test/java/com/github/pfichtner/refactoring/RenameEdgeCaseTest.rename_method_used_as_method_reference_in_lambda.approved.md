# Rename method referenced in lambda: Calculator.square → squared


### Input: Calculator.java:
```java
package com.example;

import java.util.function.IntUnaryOperator;

public class Calculator {
    public int square(int n) {
        return n * n;
    }

    public int applyTwice(int value, IntUnaryOperator op) {
        return op.applyAsInt(op.applyAsInt(value));
    }

    public int run() {
        // method reference — should be renamed with the method
        return applyTwice(3, this::square);
    }
}
```

### Refactoring:
**rename method** `Calculator.square` → `squared`  
method reference this::square in run() must also be renamed

### Output: Calculator.java:
```java
package com.example;

import java.util.function.IntUnaryOperator;

public class Calculator {
    public int squared(int n) {
        return n * n;
    }

    public int applyTwice(int value, IntUnaryOperator op) {
        return op.applyAsInt(op.applyAsInt(value));
    }

    public int run() {
        // method reference — should be renamed with the method
        return applyTwice(3, this::squared);
    }
}
```