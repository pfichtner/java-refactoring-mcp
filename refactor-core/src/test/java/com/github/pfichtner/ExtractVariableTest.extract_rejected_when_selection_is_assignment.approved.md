# Extract variable — rejected: selection is an assignment


### Input:
```java
public class Foo {
    public void run() {
        int x = 1;
        x = 2;
    }
}
```

### Refactoring:
**extract variable** `x = 2` → `val` (assignment — cannot extract)  
line 4, col 9

### Diagnostic:
```
Cannot extract an assignment expression.
```