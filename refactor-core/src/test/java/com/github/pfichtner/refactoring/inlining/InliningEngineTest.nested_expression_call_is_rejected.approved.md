# Aggressive inline — rejected: nested expression position


### Input:
```java
public class Foo {
    public int run() {
        return 1 + compute();
    }

    private int compute() {
        return 2;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 20  
hoisting before the statement would reorder evaluation relative to `1 +`

### Diagnostic:
```
call is not the whole expression of a supported statement (variable initializer, return, simple assignment, or expression statement)
```