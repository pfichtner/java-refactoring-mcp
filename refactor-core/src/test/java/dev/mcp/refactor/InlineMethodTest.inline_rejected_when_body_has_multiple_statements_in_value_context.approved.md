# Inline method — rejected: multi-statement body in value context


### Input:
```java
public class Foo {
    public int run() {
        int result = compute(5);
        return result;
    }

    private int compute(int n) {
        int doubled = n * 2;
        return doubled;
    }
}
```

### Refactoring:
**inline method** `compute(5)` at line 3, col 22  
method has 2 statements — cannot inline into an expression context

### Diagnostic:
```
Can only inline a method used as an expression if the method has exactly one statement and it is a return statement.
```