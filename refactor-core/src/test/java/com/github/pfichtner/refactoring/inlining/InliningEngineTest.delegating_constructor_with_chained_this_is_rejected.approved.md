# Aggressive inline — rejected: chained delegating constructor


### Input:
```java
public class Foo {
    private final int value;

    public Foo() {
        this(42);
    }

    public Foo(int value) {
        this(value, true);
    }

    public Foo(int value, boolean valid) {
        this.value = valid ? value : 0;
    }
}
```

### Refactoring:
**inline constructor** `this(42)` at line 5, col 9  
rejected: target also delegates via this()

### Diagnostic:
```
target constructor delegates to another constructor via this(); inline the chain one step at a time
```