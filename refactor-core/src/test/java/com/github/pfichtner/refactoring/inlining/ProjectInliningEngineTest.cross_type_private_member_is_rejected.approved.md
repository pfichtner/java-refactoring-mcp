# Project inline — rejected: cross-type private member


### Input: App.java:
```java
package com.example.app;

import com.example.base.Base;

public class App extends Base {
    public int run() {
        return peek();
    }
}
```

### Input: Base.java:
```java
package com.example.base;

public class Base {
    private int secret = 7;

    public final int peek() {
        return secret;
    }
}
```

### Refactoring:
**inline project** `peek()`  
the copied body reads a private field of the superclass; the subclass cannot access it

### Diagnostic:
```
cross-type callee references private field com.example.base.Base.secret
```