# Remove parameter — rejected: parameter used in body


### Input:
```java
package com.example;

public class Computation {
    public int add(int a, int b, int c) {
        return a + b;
    }
}
```

### Refactoring:
**remove parameter** `int a` at line 4, col 24  
a is used in the method body (return a + b)

### Diagnostic:
```
Cannot remove parameter 'a': it is referenced in the method body. Remove all usages first.
```