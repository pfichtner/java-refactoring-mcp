# Aggressive inline — rejected: explicit receiver


### Input:
```java
public class Foo {
    private int value = 1;

    public int run(Foo other) {
        return other.read();
    }

    private int read() {
        return value;
    }
}
```

### Refactoring:
**inline method** `other.read()` at line 5, col 16  
'this' inside the body refers to the receiver, not the caller

### Diagnostic:
```
instance method is invoked on an explicit receiver; 'this' would change
```