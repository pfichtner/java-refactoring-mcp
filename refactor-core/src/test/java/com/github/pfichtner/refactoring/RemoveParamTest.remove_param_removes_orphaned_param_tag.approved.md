# Remove parameter: @param tag removed from Javadoc


### Input:
```java
package com.example;

public class Calculator {

    /**
     * @param a first operand
     * @param b second operand
     * @param c carry (unused)
     * @return sum of a and b
     */
    public int add(int a, int b, int c) {
        return a + b;
    }
}
```

### Refactoring:
**remove parameter** `int c` at index 2 of `add(int a, int b, int c)`  
line 11, col 38 — @param c tag must be removed from Javadoc

### Output: Calculator.java:
```java
package com.example;

public class Calculator {

    /**
     * @param a first operand
     * @param b second operand
     * @return sum of a and b
     */
    public int add(int a, int b) {
        return a + b;
    }
}
```