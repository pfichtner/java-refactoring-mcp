# Extract method — rejected: selection contains return


### Input:
```java
public class Computation {
    public int run(int n) {
        if (n < 0) {
            return -1;
        }
        return n * 2;
    }
}
```

### Refactoring:
**extract method** selection containing `return -1;` → `validate()`  
line 3, col 9

### Diagnostic:
```
Selection contains a return statement. Cannot extract safely.
```