# rename-package


### Command:
```
rename-package --project {root} --old-package com.example.service --new-package com.example.util
```

### Exit code: 0

### Files changed: 2 | Files deleted: 1

### src/main/java/com/example/app/App.java:
```java
package com.example.app;

import com.example.util.Calculator;
import com.example.util.*;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        System.out.println(calc.add(1, 2));
    }
}
```

### src/main/java/com/example/util/Calculator.java:
```java
package com.example.util;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
```

### src/main/java/com/example/service/Calculator.java [deleted]