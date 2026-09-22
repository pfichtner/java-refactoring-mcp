# extract-interface


### Command:
```
extract-interface --file {root}/Calculator.java --name Arithmetic --output-file {root}/Arithmetic.java
```

### Exit code: 0

### Files changed: 2 | Files deleted: 0

### Arithmetic.java:
```java
public interface Arithmetic {
    int add(int a, int b);
    int subtract(int a, int b);
}
```

### Calculator.java:
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