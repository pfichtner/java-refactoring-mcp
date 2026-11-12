# Aggressive inline — rejected: non-pure preceding expression


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
sideEffect() left of + is not pure; hoisting would reorder its side effect

### Diagnostic:
```
hoisting before statement would reorder evaluation: left operand of `+` has side effects
```