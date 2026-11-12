# Aggressive inline — rejected: inner explicit receiver


### Input:
```java
public class Foo {
    private int value = 1;

    class Inner {
        int read() {
            return value;
        }

        int run(Inner other) {
            return other.read();
        }
    }
}
```

### Refactoring:
**inline method** `other.read()` at line 10, col 20  
rewriting the receiver's enclosing instance is not implemented

### Diagnostic:
```
explicit receiver inlining does not support non-static nested declaring types
```