# Inline variable — rejected: no initializer


### Input:
```java
public class Foo {
    public int compute() {
        int x;
        x = 6;
        return x;
    }
}
```

### Refactoring:
**inline variable** `x` (no initializer)  
declaration at line 3, col 13

### Diagnostic:
```
Cannot inline 'x': variable has no initializer.
```