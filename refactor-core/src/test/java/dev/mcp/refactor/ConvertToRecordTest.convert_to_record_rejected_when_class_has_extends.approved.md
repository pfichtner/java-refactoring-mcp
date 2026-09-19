# Convert to record rejected: class has extends clause


### Input: Derived.java:
```java
package com.example;

public class Derived extends Base {
    private final int value;

    public Derived(int value) {
        this.value = value;
    }

    public int getValue() { return value; }
}
```

### Refactoring:
**convert to record** `Derived` extends `Base`  
records cannot extend classes

### Diagnostic:
```
Cannot convert class 'Derived' to a record: it extends 'Base'. Records cannot extend classes.
```