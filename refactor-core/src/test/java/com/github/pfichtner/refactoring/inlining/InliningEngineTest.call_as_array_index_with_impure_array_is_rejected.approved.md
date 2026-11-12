# Aggressive inline — rejected: array index with impure array expression


### Input:
```java
public class Foo {
    public int run() {
        return makeArray()[compute()];
    }

    private int[] makeArray() {
        return new int[]{10, 20, 30};
    }

    private int compute() {
        int n = 2;
        return n;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 28  
hoisting would reorder: array expression has side effects

### Diagnostic:
```
hoisting before statement would reorder evaluation: array expression has side effects
```