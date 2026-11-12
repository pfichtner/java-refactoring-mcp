# Aggressive inline — rejected: generic explicit receiver


### Input:
```java
public class Foo<T> {
    private int value = 1;

    public int run(Foo<T> other) {
        return other.read();
    }

    private int read() {
        return value;
    }
}
```

### Refactoring:
**inline method** `other.read()` at line 5, col 16  
capturing a receiver of a generic declaring type is not implemented

### Diagnostic:
```
generic declaring types are not supported for explicit receivers
```