# Rename method: Calculator.compute → add ({@link} and @see updated)


### Input: App.java:
```java
package com.example;

/**
 * Uses the {@link Calculator#compute(int, int)} operation.
 *
 * @see Calculator#compute(int, int)
 */
public class App {
    private final Calculator calc = new Calculator();

    public int run(int x, int y) {
        return calc.compute(x, y);
    }
}
```

### Input: Calculator.java:
```java
package com.example;

public class Calculator {
    public int compute(int a, int b) {
        return a + b;
    }
}
```

### Refactoring:
**rename method** `Calculator.compute(int,int)` → `add`  
{@link Calculator#compute} and @see Calculator#compute in App.java Javadoc must be updated

### Output: App.java:
```java
package com.example;

/**
 * Uses the {@link Calculator#add(int, int)} operation.
 *
 * @see Calculator#add(int, int)
 */
public class App {
    private final Calculator calc = new Calculator();

    public int run(int x, int y) {
        return calc.add(x, y);
    }
}
```

### Output: Calculator.java:
```java
package com.example;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }
}
```