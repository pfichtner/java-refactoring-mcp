# Project inline — rejected: cross-type static call


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
    static int compute(int value) {
        return value * 2;
    }
}
```

### Refactoring:
**inline project** `Beta.compute(3)`  
the callee is static in another type; inlining would remove its class-initialization trigger

### Diagnostic:
```
cross-type static inlining would remove the class-initialization trigger; static cross-type inlining is not supported by the spike
```