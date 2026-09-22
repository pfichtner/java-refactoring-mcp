# Introduce indirection rejected: no method at offset


### Input:
```java
public class Service {

    public String process(String input) {
        return input.trim();
    }
}
```

### Refactoring:
**introduce indirection** offset inside class declaration, not a method  
line 1, col 8

### Diagnostic:
```
No method declaration found at offset 7. Place cursor inside a method signature or body.
```