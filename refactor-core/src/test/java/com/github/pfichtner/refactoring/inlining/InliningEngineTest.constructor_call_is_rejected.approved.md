# Aggressive inline — rejected: constructor call


### Input:
```java
public class Foo {
    public int run() {
        return outer(new Foo());
    }

    private int outer(Foo value) {
        return 1;
    }
}
```

### Refactoring:
**inline method** `new Foo()` at line 3, col 22  
constructors are not method calls and cannot be inlined

### Diagnostic:
```
constructor calls are not supported by aggressive inlining
```