# Convert to record rejected: has non-private-final fields


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
**convert to record** `Mutable` has non-private-final field(s): count  


### Diagnostic:
```
Class 'Mutable' has non-private-final field(s): count — records require all instance fields to be private final.
```
