# Extract interface: Addable from Calculator (subset)


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
**extract interface** `Calculator` → implements `Addable`  
selected methods: add only

### Output: Calculator.java:
```java
public class Calculator implements Addable {
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

### New: Addable.java:
```java
public interface Addable {
    int add(int a, int b);
}
```