# Move class: Calculator → com.example.util (FQN code references)


### Input: Calculator.java:
```java
package com.example.service;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
```

### Input: App.java:
```java
package com.example.app;

// No import — uses fully-qualified name throughout
public class App {
    public static void main(String[] args) {
        com.example.service.Calculator calc = new com.example.service.Calculator();
        System.out.println(calc.add(1, 2));
    }
}
```

### Refactoring:
**move class** `com.example.service.Calculator` → `com.example.util.Calculator`  
new path: Calculator.java (original file must be deleted by caller)

### Output: Calculator.java (new location):
```java
package com.example.util;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
```

### Output: App.java (updated FQN references):
```java
package com.example.app;

// No import — uses fully-qualified name throughout
public class App {
    public static void main(String[] args) {
        com.example.util.Calculator calc = new com.example.util.Calculator();
        System.out.println(calc.add(1, 2));
    }
}
```