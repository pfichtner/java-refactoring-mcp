# Move class without widen_visibility: package-private preserved


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
**move class** `com.example.Utils` → `com.util.Utils` (widen_visibility=false)  
new path: Utils.java

### Output: Utils.java (new location):
```java
package com.util;

class Utils {
    public static int doubled(int x) {
        return x * 2;
    }
}
```