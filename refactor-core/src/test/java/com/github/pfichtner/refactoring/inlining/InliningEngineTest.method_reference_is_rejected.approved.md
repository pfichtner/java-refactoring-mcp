# Aggressive inline — rejected: method reference


### Input:
```java
import java.util.function.Supplier;

public class Foo {
    public int run() {
        return outer(this::value);
    }

    private int outer(Supplier<Integer> supplier) {
        return supplier.get();
    }

    private int value() {
        return 1;
    }
}
```

### Refactoring:
**inline method** `this::value` at line 5, col 22  
a method reference is not an invocation and cannot be inlined

### Diagnostic:
```
method references are not call sites for aggressive inlining
```