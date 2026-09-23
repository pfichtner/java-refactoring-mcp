# Move class with widen_visibility: package-private → public


### Input: Utils.java (package-private):
```java
package com.example;

class Utils {
    public static int doubled(int x) {
        return x * 2;
    }
}
```

### Refactoring:
**move class** `com.example.Utils` → `com.util.Utils` (widen_visibility=true)  
new path: Utils.java

### Output: Utils.java (new location):
```java
package com.util;

public class Utils {
    public static int doubled(int x) {
        return x * 2;
    }
}
```