# Project inline: cross-type static import


### Input: App.java:
```java
package com.example.app;

import com.example.base.Base;

public class App extends Base {
    public int run() {
        return magnitude(-5);
    }
}
```

### Input: Base.java:
```java
package com.example.base;

import static java.lang.Math.abs;

public class Base {
    public final int magnitude(int value) {
        return abs(value);
    }
}
```

### Refactoring:
**inline project** `magnitude(-5)`  
the copied body calls the statically imported Math.abs; the caller gets the same static import

### Output: App.java:
```java
package com.example.app;

import com.example.base.Base;
import static java.lang.Math.abs;

public class App extends Base {
    public int run() {
        int __inline_value = -5;
		int __inline_result;
		__inline_label: {
			{
				__inline_result = abs(__inline_value);
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```