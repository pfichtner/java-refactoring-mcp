# Convert to record rejected: no private final fields


### Input: Mutable.java:
```java
package com.example;

public class Mutable {
    public int count;

    public Mutable(int count) {
        this.count = count;
    }
}
```

### Refactoring:
**convert to record** `Mutable` has no private final fields  


### Diagnostic:
```
Class 'Mutable' has no private final fields — nothing to turn into record components.
```