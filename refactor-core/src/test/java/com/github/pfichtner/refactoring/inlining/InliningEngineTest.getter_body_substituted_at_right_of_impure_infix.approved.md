# Aggressive inline: pure getter substituted at right of impure infix


### Input:
```java
public class Foo {
    int value = 0;

    public int run() {
        return sideEffect() + compute();
    }

    private int sideEffect() {
        return value++;
    }

    private int compute() {
        return 2;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 5, col 31  
single-return pure body; substituted directly without hoisting

### Output:
```java
public class Foo {
    int value = 0;

    public int run() {
        return sideEffect() + 2;
    }

    private int sideEffect() {
        return value++;
    }

    private int compute() {
        return 2;
    }
}
```