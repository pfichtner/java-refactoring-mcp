# Convert to static import — rejected: instance method


### Input:
```java
public class Foo {
    public String lower(String s) {
        return s.toLowerCase();
    }
}
```

### Refactoring:
**convert to static import** `s.toLowerCase()` at line 3, col 18  
only static methods can be statically imported

### Diagnostic:
```
'toLowerCase' is not a static method and cannot be statically imported.
```