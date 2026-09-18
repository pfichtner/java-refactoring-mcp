# Introduce parameter — rejected: selection is a simple name


### Input:
```java
package com.example;

public class Greeter {
    public void greet() {
        System.out.println("Hello, " + "World");
    }
}
```

### Refactoring:
**introduce parameter** `println` → `printer` (simple name — choose a compound expression)  
line 5, col 20

### Diagnostic:
```
Selection is already a simple name. Choose a compound expression to introduce as a parameter.
```