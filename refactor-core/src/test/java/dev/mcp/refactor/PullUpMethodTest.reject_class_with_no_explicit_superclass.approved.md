# Pull up method rejected: Animal has no superclass


### Input: Animal.java:
```java
package com.example;

public class Animal {
    public String name() {
        return "Animal";
    }
}
```

### Refactoring:
**pull up method** `Animal.name()`  
line 4, col 19

### Diagnostic:
```
Class 'Animal' has no explicit superclass — cannot pull up.
```