# Introduce parameter: FQN type inferred when Calculator not imported


### Input: App.java:
```java
package com.example.app;

// No import — Calculator referenced by FQN in method body
public class App {
    public int compute() {
        return new com.example.service.Calculator().add(1, 2);
    }

    public void run() {
        compute();
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
**introduce parameter** `new com.example.service.Calculator()` → parameter `calc`  
line 6, col 16 — type must be emitted as FQN (no import present)

### Output: App.java:
```java
package com.example.app;

// No import — Calculator referenced by FQN in method body
public class App {
    public int compute(com.example.service.Calculator calc) {
        return calc.add(1, 2);
    }

    public void run() {
        compute(new com.example.service.Calculator());
    }
}
```