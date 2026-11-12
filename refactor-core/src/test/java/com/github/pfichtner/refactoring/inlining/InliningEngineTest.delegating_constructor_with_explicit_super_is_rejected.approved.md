# Aggressive inline — rejected: delegating constructor with super()


### Input:
```java
public class Foo {
    private final int value;

    public Foo() {
        this(0);
    }

    public Foo(int value) {
        super();
        this.value = value;
    }
}
```

### Refactoring:
**inline constructor** `this(0)` at line 5, col 9  
rejected: target has explicit super() call

### Diagnostic:
```
target constructor has an explicit super() call; super() position relative to inlined body is not supported
```