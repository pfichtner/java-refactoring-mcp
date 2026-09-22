# move-static


### Command:
```
move-static --file {root}/src/main/java/com/example/MathUtils.java --line 5 --column 5 --target com.example.Helpers
```

### Exit code: 0

### Files changed: 3 | Files deleted: 0

### src/main/java/com/example/Client.java:
```java
package com.example;

public class Client {

    public int compute() {
        return Helpers.square(5);
    }
}
```

### src/main/java/com/example/Helpers.java:
```java
package com.example;

public class Helpers {

    public static int square(int x) {
        return x * x;
    }
}
```

### src/main/java/com/example/MathUtils.java:
```java
package com.example;

public class MathUtils {
}
```