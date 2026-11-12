# Project inline — cross-type static method (no class initializer)


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
Beta has no static initializer; the class-initialization check passes and the body is inlined safely

### Output: Alpha.java:
```java
package com.example;

public class Alpha {
    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 3 * 2;
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```