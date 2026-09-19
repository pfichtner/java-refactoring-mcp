# Push down field rejected: Car has no subclasses


### Input: Car.java:
```java
package com.example;

public class Car extends Vehicle {
    private int doors;

    public int topSpeed() {
        return maxSpeed;
    }
}
```

### Refactoring:
**push down field** `Car.doors`  
line 4, col 17

### Diagnostic:
```
No direct subclasses of 'Car' found in project source roots.
```