# Introduce parameter object rejected: fewer than 2 params


### Input: Printer.java:
```java
package com.example;

public class Printer {
    public void print(int x, int y, String label) {
        System.out.println(label + " at (" + x + ", " + y + ")");
    }
}
```

### Refactoring:
**introduce parameter object** only `x` specified  
line 4, col 17

### Diagnostic:
```
At least 2 parameters must be grouped into a parameter object.
```