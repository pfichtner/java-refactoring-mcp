# Rename method by name: add → plus


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
**rename method** `Calculator.add` → `Calculator.plus`  
target: method 'add'

### Output: App.java:
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

### Output: Calculator.java:
```java
package com.example;

public class Calculator {
    public int plus(int a, int b) {
        return a + b;
    }
}
```