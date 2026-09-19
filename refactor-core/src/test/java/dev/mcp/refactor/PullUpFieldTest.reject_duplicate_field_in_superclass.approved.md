# Pull up field rejected: superclass already has name


### Input: Dog.java:
```java
package com.example;

public class Dog extends Animal {
    protected String name;
    protected String breed;

    public String describe() {
        return name + " (" + breed + ")";
    }
}
```

### Refactoring:
**pull up field** `Dog.name`  
line 4, col 22

### Diagnostic:
```
Superclass 'Animal' already declares a field 'name'.
```