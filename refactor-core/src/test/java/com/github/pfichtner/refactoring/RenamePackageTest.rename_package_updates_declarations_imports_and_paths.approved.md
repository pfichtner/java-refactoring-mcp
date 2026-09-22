# Rename package: com.example.service → com.example.util


### Input: App.java:
```java
package com.example.app;

import com.example.service.Calculator;
import com.example.service.*;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        System.out.println(calc.add(1, 2));
    }
}
```

### Input: Calculator.java:
```java
package com.example.service;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
```

### Refactoring:
**rename package** `com.example.service` → `com.example.util`  
package decl in Calculator.java + single-class and wildcard imports in App.java

### Output: App.java:
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

### Output: Calculator.java:
```java
package com.example.util;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
```