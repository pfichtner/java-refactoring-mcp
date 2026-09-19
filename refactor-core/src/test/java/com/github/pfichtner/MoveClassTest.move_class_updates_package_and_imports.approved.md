# Move class: Calculator → com.example.util


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

import com.example.service.Calculator;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
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

### Output: App.java (updated import):
```java
package com.example.app;

import com.example.util.Calculator;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        System.out.println(calc.add(1, 2));
    }
}
```