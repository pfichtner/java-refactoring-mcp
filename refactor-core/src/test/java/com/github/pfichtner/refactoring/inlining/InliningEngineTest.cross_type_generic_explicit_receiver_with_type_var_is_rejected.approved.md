# Aggressive inline — rejected: cross-type generic explicit receiver uses type variable


### Input:
```java
class Box<T> {
    T item;
    final T get() {
        return item;
    }
}

public class Foo {
    public Object run(Box<String> box) {
        return box.get();
    }
}
```

### Refactoring:
**inline method** `box.get()` at line 10, col 16  
method return type references type variable T of the declaring type

### Diagnostic:
```
cross-type generic explicit receiver: method references type variable 'T' of the generic declaring type; type substitution is not supported
```