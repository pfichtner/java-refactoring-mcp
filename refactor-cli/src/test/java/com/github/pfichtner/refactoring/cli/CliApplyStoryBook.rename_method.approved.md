# rename


### Command:
```
rename --file {root}/src/main/java/com/example/Calculator.java --line 4 --column 16 --name plus
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### src/main/java/com/example/App.java:
```java
package com.example;

public class App {
    public static void main(String[] args) {
        Calculator calc = new Calculator();
        int result = calc.plus(1, 2);
        System.out.println(result);
    }
}
```

### src/main/java/com/example/Calculator.java:
```java
package com.example;

public class Calculator {
    public int plus(int a, int b) {
        return a + b;
    }
}
```