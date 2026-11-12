# Aggressive inline — rejected: call in ternary then-branch


### Input:
```java
public class Foo {
    public int run() {
        return true ? compute() : 0;
    }

    private int compute() {
        int x = 1;
        return x + 1;
    }
}
```

### Refactoring:
**inline method** `compute()` at line 3, col 23  
then-branch may not be evaluated; hoisting before statement would always evaluate it

### Diagnostic:
```
nested call in conditional expression branch: branch may not be evaluated; hoisting would always evaluate it
```