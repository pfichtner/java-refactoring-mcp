# Project inline: cross-type explicit receiver


### Input: App.java:
```java
package com.example.app;

import com.example.base.Base;

public class App {
    public int run() {
        Base other = new Base();
        return other.value();
    }
}
```

### Input: Base.java:
```java
package com.example.base;

public class Base {
    public final int value() {
        return 42;
    }
}
```

### Refactoring:
**inline project** `other.value()`  
explicit receiver captured once; body from non-subtype callee inlined into caller

### Output: App.java:
```java
package com.example.app;

import com.example.base.Base;

public class App {
    public int run() {
        Base other = new Base();
        com.example.base.Base __inline_receiver = other;
		java.util.Objects.requireNonNull(__inline_receiver);
		int __inline_result;
		__inline_label: {
			{
				__inline_result = 42;
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```