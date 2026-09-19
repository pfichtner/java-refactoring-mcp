# Pull up field rejected: Animal has no superclass


### Input: Animal.java:
```java
package com.example;

public class Animal {
    protected String name;

    public String name() {
        return name;
    }
}
```

### Refactoring:
**pull up field** `Animal.name`  
line 4, col 22

### Diagnostic:
```
Class 'Animal' has no explicit superclass — cannot pull up.
```