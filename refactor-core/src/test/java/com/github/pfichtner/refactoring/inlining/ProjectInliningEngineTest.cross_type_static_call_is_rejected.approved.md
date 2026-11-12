# Project inline — rejected: cross-type static call with class initializer


### Input: Alpha.java:
```java
package com.example;

public class Alpha {
    public int run() {
        return Beta.compute(3);
    }
}
```

### Input: Beta.java:
```java
package com.example;

public class Beta {
    static {
        // side-effecting class initializer that must not be silently removed
    }

    static int compute(int value) {
        return value * 2;
    }
}
```

### Refactoring:
**inline project** `Beta.compute(3)`  
Beta has a static initializer block; inlining would silently drop the class-initialization trigger

### Diagnostic:
```
cross-type static inlining would remove the class-initialization trigger: declaring type has a static initializer block
```