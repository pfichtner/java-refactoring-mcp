# Convert to static import — rejected: offset not on a method call


### Input:
```java
public class Foo {
    static final int LIMIT = 42;

    public int limit() {
        return LIMIT;
    }
}
```

### Refactoring:
**convert to static import** `LIMIT` at line 2, col 22  
field reference, not a method call

### Diagnostic:
```
No method call found at offset 40.
```