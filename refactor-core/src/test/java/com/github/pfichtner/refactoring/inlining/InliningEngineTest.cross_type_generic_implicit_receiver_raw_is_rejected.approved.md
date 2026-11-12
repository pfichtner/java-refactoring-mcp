# Aggressive inline — rejected: cross-type generic implicit receiver with raw type


### Input:
```java
class GenericBox<T> {
    T item;
    @SuppressWarnings("unchecked")
    final T get() {
        return (T) item;
    }
}

@SuppressWarnings("rawtypes")
public class RawBox extends GenericBox {
    public Object run() {
        return get();
    }
}
```

### Refactoring:
**inline method** `get()` at line 12, col 16  
raw generic type: type substitution not possible

### Diagnostic:
```
cross-type generic explicit receiver: raw generic type, type substitution not possible
```