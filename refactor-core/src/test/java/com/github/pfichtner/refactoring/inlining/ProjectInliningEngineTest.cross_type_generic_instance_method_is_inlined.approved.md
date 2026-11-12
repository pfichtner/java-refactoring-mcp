# Project inline: cross-type generic instance method


### Input: Box.java:
```java
package com.example.base;

public class Box<T> {
    protected T item;

    public final T get() {
        return item;
    }
}
```

### Input: StringBox.java:
```java
package com.example.app;

import com.example.base.Box;

public class StringBox extends Box<String> {
    public StringBox() {
        this.item = "Hello";
    }

    public String run() {
        return get();
    }
}
```

### Refactoring:
**inline project** `get()`  
inherited method from generic supertype Box<String>; type variable T substituted with String

### Output: StringBox.java:
```java
package com.example.app;

import com.example.base.Box;

public class StringBox extends Box<String> {
    public StringBox() {
        this.item = "Hello";
    }

    public String run() {
        String __inline_result;
		__inline_label: {
			{
				__inline_result = item;
				break __inline_label;
			}
		}
		return __inline_result;
    }
}
```