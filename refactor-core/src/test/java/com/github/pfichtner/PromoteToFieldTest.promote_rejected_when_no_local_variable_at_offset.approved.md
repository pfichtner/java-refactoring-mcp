# Promote to field rejected: no local variable at offset


### Input:
```java
public class Calc {

    int compute(int x) {
        int result = x * 2;
        return result;
    }
}
```

### Refactoring:
**promote to field** offset inside `return result`, not a local variable declaration  
line 5, col 9

### Diagnostic:
```
No local variable declaration found at offset 82. Place cursor on the variable name or type.
```