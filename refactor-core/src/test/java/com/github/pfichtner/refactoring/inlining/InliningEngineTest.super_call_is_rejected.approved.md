# Aggressive inline — rejected: super call


### Input:
```java
public class Foo extends Base {
    public int run() {
        return super.read();
    }
}

class Base {
    int read() {
        return 1;
    }
}
```

### Refactoring:
**inline method** `super.read()` at line 3, col 22  
super calls are not method invocations and have different dispatch semantics

### Diagnostic:
```
super method calls are not supported by aggressive inlining
```