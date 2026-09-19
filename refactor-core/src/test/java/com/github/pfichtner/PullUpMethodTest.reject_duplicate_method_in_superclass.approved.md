# Pull up method rejected: superclass already has name()


### Input: Dog.java:
```java
package com.example;

public class Dog extends Animal {
    @Override
    public String name() {
        return "Dog";
    }

    public void speak() {
        System.out.println("Woof!");
    }
}
```

### Refactoring:
**pull up method** `Dog.name()`  
line 5, col 19

### Diagnostic:
```
Superclass 'Animal' already declares 'name' with 0 parameter(s).
```