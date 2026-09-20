# Rename local variable with ExplicitProject: result → total


### Input:
```java
package com.example;

public class Calculator {
    public int add(int a, int b) {
        int result = a + b;
        return result;
    }
}
```

### Refactoring:
**rename local variable** `result` → `total`  
target: line 5, col 13 in Calculator.java (ExplicitProject — no build file)

### Output:
```java
package com.example;

public class Calculator {
    public int add(int a, int b) {
        int total = a + b;
        return total;
    }
}
```