# Rename package: {@link} and @see FQN in Javadoc updated


### Input: App.java:
```java
package com.example.app;

/**
 * Application entry point.
 * Delegates arithmetic to {@link com.example.service.Calculator}.
 *
 * @see com.example.service.Calculator
 */
public class App {
    public static void main(String[] args) {
        com.example.service.Calculator calc = new com.example.service.Calculator();
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
{@link com.example.service.Calculator} and @see in App.java Javadoc must be updated

### Output: App.java:
```java
package com.example.app;

/**
 * Application entry point.
 * Delegates arithmetic to {@link com.example.util.Calculator}.
 *
 * @see com.example.util.Calculator
 */
public class App {
    public static void main(String[] args) {
        com.example.util.Calculator calc = new com.example.util.Calculator();
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