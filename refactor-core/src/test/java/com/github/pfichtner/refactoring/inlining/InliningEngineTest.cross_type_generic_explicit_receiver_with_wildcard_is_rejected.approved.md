# Aggressive inline — rejected: cross-type generic explicit receiver with wildcard type argument


### Input:
```java
class Box<T> {
    T item;
    final T get() {
        return item;
    }
}

public class Foo {
    public Object run(Box<?> box) {
        return box.get();
    }
}
```

### Refactoring:
**inline method** `box.get()` at line 10, col 16  
wildcard type argument cannot be substituted

### Diagnostic:
```
cross-type generic explicit receiver: wildcard or captured type argument; type substitution not possible
```