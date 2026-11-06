# Project inline: cross-type instance method and import


### Input: App.java:
```java
package com.example.app;

import com.example.base.Base;

public class App extends Base {
    public Object run() {
        return defaultDuration();
    }
}
```

### Input: Base.java:
```java
package com.example.base;

import java.time.Duration;

public class Base {
    public final Duration defaultDuration() {
        return Duration.ofSeconds(2);
    }
}
```

### Refactoring:
**inline project** `defaultDuration()`  
the inherited final method is inlined into the subclass; the Duration import is added to the caller

### Output: App.java:
```java
package com.example.app;

import com.example.base.Base;
import java.time.Duration;

public class App extends Base {
    public Object run() {
        Duration __inline_result;
		__inline_label: {
			{
				__inline_result = Duration.ofSeconds(2);
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```