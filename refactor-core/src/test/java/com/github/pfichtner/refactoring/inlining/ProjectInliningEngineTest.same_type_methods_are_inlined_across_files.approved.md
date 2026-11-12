# Project inline: same-type methods across files


### Input: Alpha.java:
```java
package com.example;

public class Alpha {
    public int run() {
        return twice(3);
    }

    private int twice(int value) {
        return value * 2;
    }
}
```

### Input: Beta.java:
```java
package com.example;

public class Beta {
    public int run() {
        return plusTen(5);
    }

    private int plusTen(int value) {
        return value + 10;
    }
}
```

### Refactoring:
**inline project** `twice(3)` and `plusTen(5)`  
two files change; cross-file bindings resolve, but each call and callee stay in the same type

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

    private int twice(int value) {
        return value * 2;
    }
}
```

### Output: Beta.java:
```java
package com.example;

public class Beta {
    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 5 + 10;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int plusTen(int value) {
        return value + 10;
    }
}
```