# Push down method rejected: Dog has no subclasses


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
**push down method** `Dog.speak()`  
line 9, col 17

### Diagnostic:
```
No direct subclasses of 'Dog' found in project source roots.
```