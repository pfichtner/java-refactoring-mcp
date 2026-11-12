# Aggressive inline — rejected: direct recursion


### Input:
```java
public class Foo {
    public int run() {
        return countdown(3);
    }

    private int countdown(int value) {
        if (value <= 0) {
            return 0;
        }
        return countdown(value - 1);
    }
}
```

### Refactoring:
**inline method** `countdown(value - 1)` at line 10, col 16  
inlining a call into its own declaration cannot terminate

### Diagnostic:
```
recursive call
```