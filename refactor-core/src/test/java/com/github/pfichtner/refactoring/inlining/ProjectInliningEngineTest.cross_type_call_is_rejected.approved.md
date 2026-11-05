# Project inline — rejected: cross-type call


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
the callee lives in another type; import rewriting is deferred

### Diagnostic:
```
cross-type inlining is not supported by the spike
```