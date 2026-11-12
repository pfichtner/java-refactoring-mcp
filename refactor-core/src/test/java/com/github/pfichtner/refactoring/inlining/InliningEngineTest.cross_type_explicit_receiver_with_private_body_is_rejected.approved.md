# Aggressive inline — rejected: cross-type explicit receiver body has private field


### Input:
```java
class Base {
    private int secret = 1;

    final int read() {
        return secret;
    }
}

public class Foo {
    public int run(Base other) {
        return other.read();
    }
}
```

### Refactoring:
**inline method** `other.read()` at line 11, col 16  
body references a private field inaccessible from the caller

### Diagnostic:
```
cross-type callee references private field Base.secret
```