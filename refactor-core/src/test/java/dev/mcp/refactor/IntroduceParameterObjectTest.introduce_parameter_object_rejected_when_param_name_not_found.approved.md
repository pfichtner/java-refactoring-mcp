# Introduce parameter object rejected: unknown param name


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
**introduce parameter object** `x, unknown` (unknown does not exist)  
line 4, col 17

### Diagnostic:
```
Parameter 'unknown' not found in method 'print'.
```