# Aggressive inline — rejected: call in multi-fragment declaration with impure earlier


### Input:
```java
public class Foo {
    public int run() {
        int a = sideEffect(), b = compute();
        return a + b;
    }

    private int sideEffect() {
        return 1;
    }

    private int compute() {
        int n = 5;
        return n;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 35  
earlier variable initializer has side effects

### Diagnostic:
```
hoisting before statement would reorder evaluation: earlier variable initializer has side effects
```