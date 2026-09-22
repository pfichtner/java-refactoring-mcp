# Rename package with FQN code references: com.example.service → com.example.util


### Input: App.java:
```java
package com.example.app;

import java.util.function.Supplier;

// No import for Calculator — uses fully-qualified names throughout
public class App {

    // FQN in field type and initializer
    private com.example.service.Calculator calc = new com.example.service.Calculator();

    public static void main(String[] args) {
        // FQN in local variable declaration
        com.example.service.Calculator c = new com.example.service.Calculator();

        // FQN inside a lambda
        Supplier<com.example.service.Calculator> factory =
                () -> new com.example.service.Calculator();

        System.out.println(c.add(1, 2));
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
FQN in field type, local variable, lambda body

### Output: App.java:
```java
package com.example.app;

import java.util.function.Supplier;

// No import for Calculator — uses fully-qualified names throughout
public class App {

    // FQN in field type and initializer
    private com.example.util.Calculator calc = new com.example.util.Calculator();

    public static void main(String[] args) {
        // FQN in local variable declaration
        com.example.util.Calculator c = new com.example.util.Calculator();

        // FQN inside a lambda
        Supplier<com.example.util.Calculator> factory =
                () -> new com.example.util.Calculator();

        System.out.println(c.add(1, 2));
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