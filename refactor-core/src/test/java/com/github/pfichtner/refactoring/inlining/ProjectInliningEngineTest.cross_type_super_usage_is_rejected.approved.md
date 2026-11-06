# Project inline — rejected: cross-type body uses super


### Input: App.java:
```java
package com.example.app;

import com.example.base.Base;

public class App extends Base {
    public String run() {
        return describe();
    }
}
```

### Input: Base.java:
```java
package com.example.base;

public class Base {
    public final String describe() {
        return super.toString();
    }
}
```

### Refactoring:
**inline project** `describe()`  
the copied body contains a super call; it would bind to App's superclass

### Diagnostic:
```
cross-type callee body uses 'super'; it would bind to the caller's superclass
```