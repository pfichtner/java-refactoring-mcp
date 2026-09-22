# Inline method: add (multi-file, declaration removed)


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

### Output: Calculator.java:
```java
package com.example;

public class Calculator {
}
```