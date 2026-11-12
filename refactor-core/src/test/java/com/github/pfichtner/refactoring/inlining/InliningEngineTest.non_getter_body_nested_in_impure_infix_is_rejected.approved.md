# Aggressive inline — rejected: non-getter body in impure infix


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
        int x = this.value;
        return x + 1;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 5, col 31  
multi-statement body is not a getter; hoisting would reorder sideEffect()

### Diagnostic:
```
hoisting before statement would reorder evaluation: left operand of `+` has side effects
```