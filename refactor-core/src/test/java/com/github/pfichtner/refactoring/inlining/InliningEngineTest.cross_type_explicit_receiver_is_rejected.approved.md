# Aggressive inline — rejected: cross-type explicit receiver


### Input:
```java
class Base {
    final int read() {
        return 1;
    }
}

public class Foo extends Base {
    public int run(Base other) {
        return other.read();
    }
}
```

### Refactoring:
**inline method** `other.read()` at line 9, col 16  
receiver rewriting for the cross-type case is not implemented

### Diagnostic:
```
cross-type explicit receiver inlining is not supported
```