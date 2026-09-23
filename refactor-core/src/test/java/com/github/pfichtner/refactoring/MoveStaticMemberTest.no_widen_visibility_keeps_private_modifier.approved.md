# Move static without widen_visibility: private modifier preserved


### Input: MathUtils.java:
```java
package com.example;

public class MathUtils {
    private static int square(int x) {
        return x * x;
    }
}
```

### Input: Helpers.java:
```java
package com.example;

public class Helpers {
}
```

### Refactoring:
**move static member** `MathUtils.square(int)` → `Helpers.square(int)` (widen_visibility=false)  
line 4, col 24

### Output: MathUtils.java:
```java
package com.example;

public class MathUtils {
}
```

### Output: Helpers.java:
```java
package com.example;

public class Helpers {

    private static int square(int x) {
        return x * x;
    }
}
```