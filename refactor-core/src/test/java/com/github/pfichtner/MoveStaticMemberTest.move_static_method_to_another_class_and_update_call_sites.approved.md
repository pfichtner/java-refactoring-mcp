# Move static method MathUtils.square → Helpers.square


### Input: MathUtils.java:
```java
package com.example;

public class MathUtils {

    public static int square(int x) {
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

### Input: Client.java:
```java
package com.example;

public class Client {

    public int compute() {
        return MathUtils.square(5);
    }
}
```

### Refactoring:
**move static member** `MathUtils.square(int)` → `Helpers.square(int)`; call sites updated  
line 5, col 23

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

    public static int square(int x) {
        return x * x;
    }
}
```

### Output: Client.java:
```java
package com.example;

public class Client {

    public int compute() {
        return Helpers.square(5);
    }
}
```