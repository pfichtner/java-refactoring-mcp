# Inline method: add (multi-file, all call sites, declaration kept)


### Input: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        int result = calc.add(1, 2);
        System.out.println(result);
    }
}
```

### Input: Calculator.java:
```java
package com.example;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
```

### Refactoring:
**inline method** `Calculator.add(int a, int b)` → inlined at all call sites  
call site in App.java replaced with expression; Calculator.java unchanged

### Output: App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        int result = 1 + 2;
        System.out.println(result);
    }
}
```