# Convert nested rejected: no nested type at offset


### Input: Outer.java:
```java
package com.example;

public class Outer {

    private String name;

    public Outer(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    private static class Helper {

        static int doubleIt(int x) {
            return x * 2;
        }
    }
}
```

### Refactoring:
**convert nested to top-level** offset inside method `getName()`, not a nested type  
line 11, col 19

### Diagnostic:
```
No nested type declaration found at offset 153.
```