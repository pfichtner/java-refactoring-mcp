# Project inline — rejected: cross-type import conflict


### Input: App.java:
```java
package com.example.app;

import com.example.base.Base;
import com.example.other.Duration;

public class App extends Base {
    public long run() {
        return defaultSeconds();
    }
}
```

### Input: Base.java:
```java
package com.example.base;

import java.time.Duration;

public class Base {
    public final long defaultSeconds() {
        return Duration.ofSeconds(2).getSeconds();
    }
}
```

### Input: Duration.java:
```java
package com.example.other;

public class Duration {
}
```

### Refactoring:
**inline project** `defaultSeconds()`  
the callee needs java.time.Duration, but the caller already imports a different Duration

### Diagnostic:
```
import conflict: 'Duration' is already com.example.other.Duration but the callee needs java.time.Duration
```