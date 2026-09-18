# Extract constant — rejected: selection is a simple name


### Input:
```java
public class Foo {
    public void run() {
        int x = 5;
        System.out.println(x);
    }
}
```

### Refactoring:
**extract constant** `x` → `X` (simple name — nothing to extract)  
line 3, col 13

### Diagnostic:
```
Selection is already a simple name — nothing to extract.
```