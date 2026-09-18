# Extract method — rejected: multiple variables used after selection


### Input:
```java
public class Computation {
    public int run() {
        int a = 3;
        int b = 4;
        System.out.println(a + b);
        return a * b;
    }
}
```

### Refactoring:
**extract method** selection → `init()` (would need to return a and b)  
line 3, col 9

### Diagnostic:
```
Cannot extract: multiple variables would need to be returned (a, b). Narrow the selection.
```