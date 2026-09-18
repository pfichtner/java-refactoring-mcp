# Extract interface: Arithmetic from Calculator


### Input: Calculator.java:
```java
public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    private int helper(int x) {
        return x * 2;
    }
}
```

### Refactoring:
**extract interface** `Calculator` → implements `Arithmetic`  
all public non-static methods: add, subtract (private helper excluded)

### Output: Calculator.java:
```java
public class Calculator implements Arithmetic {
    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    private int helper(int x) {
        return x * 2;
    }
}
```

### New: Arithmetic.java:
```java
public interface Arithmetic {
    int add(int a, int b);
    int subtract(int a, int b);
}
```