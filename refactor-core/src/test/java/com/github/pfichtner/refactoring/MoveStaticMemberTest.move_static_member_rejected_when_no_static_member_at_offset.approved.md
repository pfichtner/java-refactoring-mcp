# Move static member rejected: no static member at offset


### Input: MathUtils.java:
```java
package com.example;

public class MathUtils {

    public static int square(int x) {
        return x * x;
    }
}
```

### Refactoring:
**move static member** offset inside class declaration, not a static method or field  
line 3, col 8

### Diagnostic:
```
No static method or static field declaration found at offset 29.
```