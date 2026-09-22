# Introduce parameter: @param tag inserted after last @param


### Input:
```java
package com.example;

public class Calculator {

    /**
     * @param a first operand
     * @param b second operand
     * @return sum
     */
    public int add(int a, int b) {
        return a + b;
    }
}
```

### Refactoring:
**introduce parameter** `a + b` → parameter `int value`  
line 11, col 16 — @param value must be added to Javadoc

### Output: Calculator.java:
```java
package com.example;

public class Calculator {

    /**
     * @param a first operand
     * @param b second operand
     * @param value
     * @return sum
     */
    public int add(int a, int b, int value) {
        return value;
    }
}
```