# Aggressive inline — rejected: synchronized method


### Input:
```java
public class Foo {
    public int run() {
        return guarded(2);
    }

    private synchronized int guarded(int value) {
        return value + 1;
    }
}
```

### Refactoring:
**inline method** `guarded(2)` at line 3, col 16  
inlining would remove the method-level monitor

### Diagnostic:
```
synchronized method: inlining would remove the method-level monitor
```