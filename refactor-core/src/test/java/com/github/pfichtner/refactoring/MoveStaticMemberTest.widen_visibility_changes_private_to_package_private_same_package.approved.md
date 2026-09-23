# Move static with widen_visibility (same package): private → package-private


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
**move static member** `MathUtils.square(int)` → `Helpers.square(int)` (widen_visibility=true, same package → package-private)  
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

    static int square(int x) {
        return x * x;
    }
}
```