# Rename local variable in Maven project: result → sum


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
**rename local variable** `result` → `sum`  
target: line 5, col 13 in Calculator.java (Maven project context)

### Output:
```java
package com.example;

public class Calculator {
    public int add(int a, int b) {
        int sum = a + b;
        return sum;
    }
}
```