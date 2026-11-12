# Aggressive inline — rejected: side-effecting assignment LHS


### Input:
```java
public class Foo {
    private int[] values = new int[3];

    public void run() {
        values[index()] = compute();
    }

    private int index() {
        return 0;
    }

    private int compute() {
        return 1;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 5, col 27  
array index evaluation would be reordered relative to the inlined body

### Diagnostic:
```
assignment left-hand side has side effects or may be affected by hoisting; receiver or index evaluation would be reordered
```
