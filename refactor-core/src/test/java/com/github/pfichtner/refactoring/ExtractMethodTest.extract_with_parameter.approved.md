# Extract method: compute(n) — with parameter


### Input:
```java
public class Computation {
    public void run() {
        int n = 10;
        int result = n * 2;
        System.out.println(result);
    }
}
```

### Refactoring:
**extract method** selection → `compute(int n)`  
line 4, col 9 to line 5, col 35

### Output:
```java
public class Computation {
    public void run() {
        int n = 10;
        compute(n);
    }

    private void compute(int n) {
        int result = n * 2;
        System.out.println(result);
    }
}
```